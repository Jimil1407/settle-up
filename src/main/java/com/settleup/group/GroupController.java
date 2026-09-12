package com.settleup.group;

import com.settleup.group.dto.AddMemberRequest;
import com.settleup.group.dto.CreateGroupRequest;
import com.settleup.group.dto.GroupBalancesResponse;
import com.settleup.group.dto.GroupView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupView> create(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(userId, request));
    }

    @GetMapping
    public List<GroupView> list(@AuthenticationPrincipal Long userId) {
        return groupService.listForUser(userId);
    }

    @GetMapping("/{groupId}")
    public GroupView get(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        return groupService.get(groupId, userId);
    }

    @PostMapping("/{groupId}/members")
    public GroupView addMember(@PathVariable Long groupId,
                               @AuthenticationPrincipal Long userId,
                               @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(groupId, userId, request);
    }

    @GetMapping("/{groupId}/balances")
    public GroupBalancesResponse balances(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        return groupService.balances(groupId, userId);
    }
}
