package com.settleup.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks the API exactly as the frontend does: register, create a group, invite a flatmate, add
 * expenses, read balances, fetch the settlement plan, pay it off.
 *
 * <p>Covers the wiring that unit tests cannot — JSON shapes, auth, status codes and the
 * {@code Idempotency-Key} header contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("the full flatmate journey, end to end")
    void fullJourney() throws Exception {
        String aditiToken = register("Aditi");
        String rohanEmail = emailFor("Rohan");
        String rohanToken = register("Rohan", rohanEmail);

        long aditiId = userIdOf(aditiToken);
        long rohanId = userIdOf(rohanToken);

        // Create a group
        JsonNode group = json(mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + aditiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Flat 402", "currency", "INR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Flat 402"))
                .andReturn());
        long groupId = group.get("id").asLong();

        // Invite the flatmate
        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + aditiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", rohanEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));

        // Aditi pays the internet bill
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + aditiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "description", "Internet bill",
                                "amount", "1200.00",
                                "paidByUserId", aditiId,
                                "splitType", "EQUAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formattedTotal").value("\u20b91200.00"))
                .andExpect(jsonPath("$.splits.length()").value(2));

        // Rohan sees what he owes
        JsonNode balances = json(mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + rohanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settled").value(false))
                .andReturn());

        long sum = 0;
        for (JsonNode b : balances.get("balances")) {
            sum += b.get("balancePaise").asLong();
        }
        assertThat(sum).as("balances returned by the API must net to zero").isZero();

        // The settlement plan
        JsonNode plan = json(mockMvc.perform(get("/api/groups/" + groupId + "/settlement-plan")
                        .header("Authorization", "Bearer " + rohanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers.length()").value(1))
                .andReturn());

        long version = plan.get("version").asLong();
        JsonNode transfer = plan.get("transfers").get(0);
        assertThat(transfer.get("fromUserId").asLong()).isEqualTo(rohanId);
        assertThat(transfer.get("toUserId").asLong()).isEqualTo(aditiId);
        assertThat(transfer.get("amountPaise").asLong()).isEqualTo(60_000L);

        // Rohan pays up
        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + rohanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "fromUserId", rohanId,
                                "toUserId", aditiId,
                                "amount", "600.00",
                                "expectedVersion", version))))
                .andExpect(status().isCreated());

        // Everyone is square
        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + rohanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settled").value(true));
    }

    @Test
    @DisplayName("a stale settlement plan is rejected with 409 and explains itself")
    void stalePlanReturnsConflict() throws Exception {
        String token = register("Solo");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Stale API");

        addExpense(token, groupId, userId, "500.00");
        long staleVersion = json(mockMvc.perform(get("/api/groups/" + groupId + "/settlement-plan")
                .header("Authorization", "Bearer " + token)).andReturn()).get("version").asLong();

        // Someone adds another expense before the plan is acted on.
        addExpense(token, groupId, userId, "250.00");

        mockMvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "fromUserId", userId,
                                "toUserId", userId,
                                "amount", "100.00",
                                "expectedVersion", staleVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.meta.expectedVersion").value(staleVersion))
                .andExpect(jsonPath("$.meta.currentVersion").value(staleVersion + 1));
    }

    @Test
    @DisplayName("replaying a request with the same Idempotency-Key does not double-charge")
    void idempotencyKeyIsHonoured() throws Exception {
        String token = register("Retry");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Retry group");
        String key = "expense-key-" + System.nanoTime();

        String payload = body(Map.of(
                "description", "Dinner",
                "amount", "800.00",
                "paidByUserId", userId,
                "splitType", "EQUAL"));

        JsonNode first = json(mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode replay = json(mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(replay.get("id").asLong()).isEqualTo(first.get("id").asLong());

        mockMvc.perform(get("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("requests without a token are rejected")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a non-member cannot read another group's balances")
    void enforcesGroupMembership() throws Exception {
        String insiderToken = register("Insider");
        String outsiderToken = register("Outsider");
        long groupId = createGroup(insiderToken, "Private");

        mockMvc.perform(get("/api/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an expense larger than allowed precision is rejected before it reaches the ledger")
    void validatesRequestBodies() throws Exception {
        String token = register("Validator");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Validation");

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "description", "Too precise",
                                "amount", "10.999",
                                "paidByUserId", userId,
                                "splitType", "EQUAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(1));
    }

    @Test
    @DisplayName("an exact split whose parts do not add up is rejected")
    void rejectsMismatchedExactSplit() throws Exception {
        String token = register("Exact");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Exact split");

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "description", "Mismatched",
                                "amount", "100.00",
                                "paidByUserId", userId,
                                "splitType", "EXACT",
                                "exactAmounts", Map.of(String.valueOf(userId), "60.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("add up to")));
    }

    @Test
    @DisplayName("expenses can be exported as CSV")
    void exportsCsv() throws Exception {
        String token = register("Exporter");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Export");
        addExpense(token, groupId, userId, "450.00");

        String csv = mockMvc.perform(get("/api/groups/" + groupId + "/export/expenses.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("Date,Description,Paid By,Total,Split Type,Participant,Share");
        assertThat(csv).contains("450.00");
    }

    @Test
    @DisplayName("the cross-group summary reports where a user stands overall")
    void summarisesAcrossGroups() throws Exception {
        String token = register("Summary");
        long userId = userIdOf(token);
        long groupId = createGroup(token, "Summary group");
        addExpense(token, groupId, userId, "300.00");

        mockMvc.perform(get("/api/users/me/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.groups.length()").value(1));
    }

    // --- helpers ------------------------------------------------------------

    private String register(String name) throws Exception {
        return register(name, emailFor(name));
    }

    private String register(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "email", email,
                                "displayName", name,
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("token").asText();
    }

    private long createGroup(String token, String name) throws Exception {
        return json(mockMvc.perform(post("/api/groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", name, "currency", "INR"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private void addExpense(String token, long groupId, long payerId, String amount) throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "description", "Expense",
                                "amount", amount,
                                "paidByUserId", payerId,
                                "splitType", "EQUAL"))))
                .andExpect(status().isCreated());
    }

    private long userIdOf(String token) throws Exception {
        return json(mockMvc.perform(get("/api/users/me/summary")
                .header("Authorization", "Bearer " + token))
                .andReturn()).get("userId").asLong();
    }

    private static String emailFor(String name) {
        return name.toLowerCase() + "-api-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    private String body(Map<String, ?> content) throws Exception {
        return objectMapper.writeValueAsString(content);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
