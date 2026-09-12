# SettleUp

Shared-expense settlement for flatmates and trips, built around one guarantee: **concurrent writes
can never make a group's money wrong.**

Everyone has been in the WhatsApp group where six people paid for different things, nobody knows
who owes whom, and the UPI screenshots are lost somewhere above the memes. SettleUp works out the
net position of each person and reduces the whole tangle to the smallest possible set of payments.

The interesting part is not the CRUD. It is what happens when three flatmates add expenses at the
same time while a fourth is halfway through settling up.

---

## Contents

- [What makes this non-trivial](#what-makes-this-non-trivial)
- [Running it](#running-it)
- [Architecture](#architecture)
- [The four design decisions that matter](#the-four-design-decisions-that-matter)
- [Debt simplification](#debt-simplification)
- [Testing](#testing)
- [API](#api)
- [Frontend](#frontend)
- [A bug the test suite caught](#a-bug-the-test-suite-caught)
- [What I would do next](#what-i-would-do-next)

---

## What makes this non-trivial

| Problem | Approach |
|---|---|
| Splitting ₹100 three ways loses a paisa | Integer paise everywhere + largest-remainder distribution |
| Concurrent writes losing each other's updates | `SELECT … FOR UPDATE` on the group row, serialising balance writes |
| Acting on balances that changed while you were looking | Version-stamped settlement plans, rejected with `409` when stale |
| Retrying a request on a flaky mobile network | `Idempotency-Key` with a Redis fast path and a unique index as the real guarantee |
| A monthly rent job that runs twice | Generation keyed by `(template, period)` with a unique constraint |
| Proving any of the above actually works | 62 tests, including 200 concurrent writes asserting the ledger still sums to zero |

---

## Running it

**No infrastructure required** — just a JDK 17:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

(The quotes matter in PowerShell, which otherwise mangles the `-D` argument.)

This runs on an in-memory database with in-memory stand-ins for Redis. The API comes up on
`http://localhost:8080`, Swagger UI on `http://localhost:8080/swagger-ui.html`.

**The real stack** (PostgreSQL + Redis):

```powershell
docker compose up --build
```

**Frontend:**

```powershell
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to :8080
```

The marketing site is at `/`; the working app lives behind sign-in at `/app`.

**Tests:**

```powershell
mvn test             # 62 tests, no Docker needed
```

---

## Architecture

One Spring Boot service. Not microservices — there is no independent scaling or deployment story
here that would justify the operational cost, and splitting a ledger across service boundaries
would turn a database transaction into a distributed one for no benefit.

```
                     ┌──────────────────────────────┐
   React + TS  ──────▶  REST API (Spring Boot 3.4)  │
                     └───────────────┬──────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
      ┌───────────────┐     ┌────────────────┐     ┌────────────────┐
      │ ExpenseService│     │SettlementServ. │     │ RecurringJob   │
      └───────┬───────┘     └────────┬───────┘     └────────┬───────┘
              │                      │                      │
              └──────────┬───────────┴──────────────────────┘
                         ▼
                ┌──────────────────┐
                │ GroupLockService │  ← every balance write passes through here
                └────────┬─────────┘
                         │ SELECT … FOR UPDATE
                         ▼
                ┌──────────────────┐        ┌──────────────────────┐
                │  LedgerService   │───────▶│ balance_entry        │
                │  (only writer)   │        │ (append-only)        │
                └──────────────────┘        └──────────────────────┘

   Redis ──▶ idempotency keys · version-keyed balance cache · rate limiting
```

**Package layout**

```
com.settleup
├── common/       Money (paise arithmetic), errors, health
├── auth/         JWT issue/verify, registration, login
├── group/        Groups, membership, GroupLockService  ← the locking protocol
├── expense/      Expenses, splits, SplitType
├── ledger/       BalanceEntry + LedgerService          ← the only ledger writer
├── settlement/   DebtSimplifier + settlement recording
├── recurring/    Monthly template generation
├── infra/        Redis adapters, each with an in-memory twin
├── notification/ Async fan-out on a bounded pool
└── export/       CSV export
```

---

## The four design decisions that matter

### 1. Money is integer paise, never a float

`0.1 + 0.2 != 0.3` in binary floating point. In a ledger, a fraction of a paisa of drift per
expense compounds into balances that visibly don't add up.

Everything is a `long` count of paise. The API accepts decimal rupees (what a person types) and
converts once, at the edge.

Splitting is the subtle part. ₹100 across three people is ₹33.333…, and naive rounding gives
₹33.33 each — which is ₹99.99. The missing paisa has to go somewhere, so the
**largest-remainder method** gives it to the first participant by id:

```
10000 paise / 3  →  3334, 3333, 3333   (sums to exactly 10000)
```

Deterministic, and it sums back to the total for any amount and any group size. There is a
parameterised test asserting exactly that across a range of awkward values.

### 2. Balances are derived from an append-only ledger, not stored

There is no `balance` column anywhere. Every expense and every settlement appends signed deltas to
`balance_entry`, and a user's balance is `SUM(delta_paise)`.

```
Aditi pays ₹900 for dinner, split 3 ways:

  balance_entry:  (Aditi, +90000)   ← she is out of pocket
                  (Aditi, -30000)   ← her own share
                  (Rohan, -30000)
                  (Sana,  -30000)
                  ─────────────────
                  sum       0       ← always
```

Three things fall out of this for almost no added complexity:

- **No lost updates.** Inserts never read-modify-write a running total, so nothing can be clobbered.
- **A free audit trail.** Rows are only ever inserted. Every paisa traces to the expense or
  settlement that caused it.
- **A testable invariant.** *A group's entries must sum to exactly zero.* One assertion catches
  virtually every money bug, and it is asserted after every concurrency test.

`LedgerService` is the only component that may write entries, and it refuses to persist a batch
that doesn't net to zero — so a bad split fails at the moment it would corrupt the ledger, not
weeks later.

### 3. One lock per group, taken the same way every time

Every balance-changing write begins by taking `PESSIMISTIC_WRITE` on the group row:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select g from ExpenseGroup g where g.id = :id")
Optional<ExpenseGroup> findByIdForUpdate(@Param("id") Long id);
```

Group-level granularity is a deliberate trade. Groups are small and low-traffic, so serialising
writes within one group costs nothing in practice, and in exchange every balance mutation is
trivially serialised. Because the lock is always taken on a single, always-present row, lock
ordering is consistent by construction and the protocol cannot deadlock against itself.

All of it lives in one class, `GroupLockService`, so "how do we stay consistent?" has exactly one
answer to audit.

### 4. Settlement plans carry a version

A settlement plan is a snapshot. If Rohan opens the settle-up screen and Aditi adds an expense
before he taps pay, the amounts on his screen are fiction.

Every balance write bumps the group's `version`. The plan reports the version it was computed
from, the client echoes it back, and a mismatch is rejected:

```http
POST /api/groups/1/settlements
{ "fromUserId": 2, "toUserId": 1, "amount": "500.00", "expectedVersion": 1 }

HTTP 409 Conflict
{
  "message": "settlement plan was computed at version 1 but the group is now at version 2;
              re-fetch the plan and try again",
  "meta": { "expectedVersion": 1, "currentVersion": 2 }
}
```

The frontend handles this by refreshing and telling the user what happened, rather than failing
opaquely.

That same version keys the balance cache — entries are stored under
`settleup:balances:{groupId}:v{version}`. Because a write produces a new version, it also produces
a new cache key, so the stale entry becomes unreachable without an explicit delete. That sidesteps
the classic invalidation race where a writer deletes a key a moment before a slow reader writes a
stale value back.

---

## Debt simplification

Raw expense history produces a mess of pairwise IOUs. Only each person's **net** position matters,
so the individual debts are discarded and a minimal payment set is re-derived from the net balances
using two heaps:

1. Put creditors in one max-heap, debtors in another.
2. Match the largest creditor with the largest debtor, transfer `min(credit, debt)`.
3. Push back whatever remains and repeat.

Each transfer zeroes out at least one person, so `n` non-zero participants need at most `n - 1`
payments, in `O(n log n)`.

```
Before                          After
──────                          ─────
Aditi  is owed  ₹300            Rohan pays Aditi ₹100
Rohan  owes     ₹100            Sana  pays Aditi ₹200
Sana   owes     ₹200
```

**This is a greedy heuristic, not a proven optimum — and that is a deliberate choice.** Finding the
true minimum number of transfers is NP-hard: it generalises subset-sum, because you would have to
find every subset of participants whose balances cancel to zero and settle each independently.
Exponential work to maybe save one payment in a five-person flat is not a good trade. The `n - 1`
bound is already a large reduction and it runs instantly.

A randomised test runs 500 trials of up to 13 people, asserting the plan both respects the `n - 1`
bound and drives every balance to exactly zero.

---

## Testing

```
62 tests, no Docker required

MoneyTest ......................... 21   splitting, rounding, rupee/paise conversion
DebtSimplifierTest .................. 8   including 500 randomised trials
LedgerIntegrationTest ............... 7   ledger correctness across split types
SettlementServiceIntegrationTest .... 7   plans, stale rejection, idempotency
RecurringExpenseJobTest ............. 6   re-run safety, month clamping
ApiFlowTest ......................... 9   end-to-end HTTP, auth, status codes
ConcurrentBalanceIntegrityTest ...... 4   the ones that matter
```

The suite runs against an in-memory database with in-memory adapters standing in for Redis, so
`mvn test` works on any machine with a JDK and CI needs no service containers. The concurrency
tests still exercise genuine row-level locking.

### The concurrency tests

All four hammer a single group from 50 threads, released simultaneously by a `CountDownLatch` so
they genuinely collide rather than trickling in and accidentally serialising themselves.

**200 concurrent expenses on one group.** Uses ₹99.99 split 5 ways — deliberately indivisible, so
any sloppy rounding is amplified 200×. Asserts every expense persisted exactly once, that balances
land on their precise expected values, and that the ledger sums to zero.

**Expenses and settlements interleaved.** 200 mixed operations racing on the same group; the ledger
must stay balanced throughout.

**Version integrity.** 60 concurrent writes must advance the group version by exactly 60 — no
skipped increments, no clobbered ones. *This is the test that caught the bug described below.*

**Idempotency under contention.** 40 identical requests sharing one `Idempotency-Key` must produce
exactly one expense.

---

## API

All endpoints except `/api/auth/*` and `/api/health` require `Authorization: Bearer <jwt>`.
Write endpoints accept an optional `Idempotency-Key` header.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Obtain a token |
| `GET` | `/api/groups` | Groups you belong to |
| `POST` | `/api/groups` | Create a group |
| `POST` | `/api/groups/{id}/members` | Add someone by email (rate limited) |
| `GET` | `/api/groups/{id}/balances` | Net position per member |
| `POST` | `/api/groups/{id}/expenses` | Record an expense |
| `GET` | `/api/groups/{id}/expenses` | Expense history |
| `GET` | `/api/groups/{id}/settlement-plan` | Minimal payment set + version |
| `POST` | `/api/groups/{id}/settlements` | Record a payment |
| `GET` | `/api/groups/{id}/ledger-check` | Live sum-to-zero check |
| `GET` | `/api/groups/{id}/export/expenses.csv` | CSV export |
| `POST` | `/api/groups/{id}/recurring` | Standing monthly expense |
| `GET` | `/api/users/me/summary` | Position across all groups |

`GET /api/groups/{id}/ledger-check` exposes the core invariant at runtime:

```json
{ "groupId": 1, "imbalancePaise": 0, "balanced": true }
```

Point a load generator at a group, poll this, and watch it stay at zero.

---

## A bug the test suite caught

Worth writing down, because it is the kind of bug that ships silently.

The original version bumped the group version the idiomatic JPA way — take the row lock, then ask
Hibernate to force-increment the `@Version` field:

```java
entityManager.lock(group, LockModeType.OPTIMISTIC_FORCE_INCREMENT);   // does nothing here
```

This compiles, throws nothing, logs nothing — **and never increments the version.** Hibernate
treats lock modes as a hierarchy and refuses to downgrade. The entity was already held at
`PESSIMISTIC_WRITE`, which outranks `OPTIMISTIC_FORCE_INCREMENT`, so the request was silently
discarded.

Nothing would have looked broken. Balances would have stayed correct. But the version would have
been frozen at 0 forever, which means the balance cache would have served stale data indefinitely
and *stale-plan detection would never have fired* — the exact feature it existed to provide, quietly
disabled.

The `versionAdvancesExactlyOncePerWrite` test failed with `expected: 60 but was: 0`.

The fix is an explicit, unambiguous increment under the lock:

```java
@Modifying(flushAutomatically = true)
@Query("update ExpenseGroup g set g.version = g.version + 1 where g.id = :id")
int incrementVersion(@Param("id") Long id);
```

Less magical, and it actually happens. The obvious-looking alternative — dirty-checking a
`lastUpdated` timestamp — has the same failure mode: two commits in the same clock tick write an
identical value, Hibernate detects no change, and the version silently stalls again.

---

## Frontend

A marketing site at `/` and the working app behind sign-in at `/app`, sharing one design system.

**Stack:** React 18 + TypeScript + Vite, Tailwind CSS, Framer Motion, React Router.

Vite rather than Next.js: this is a single-page app talking to a Spring Boot API, so there is no
server-rendering or routing work for Next to do. Adding it would mean a second runtime to deploy
for no benefit the API does not already provide.

**Design system.** Semantic CSS custom properties (`--surface`, `--border`, `--text`) flip between
light and dark, so components never branch on theme — `bg-[rgb(var(--surface))]` is correct in
both. Theme is resolved from `localStorage`, falling back to the OS preference, and applied by an
inline script before first paint so a light-mode visitor never sees a dark flash.

**Motion.** One shared easing curve across the whole site; mixing eases is the fastest way to look
unconsidered. Scroll reveals fire once and slightly early, so content reads as already in motion
rather than popping in. The ambient gradients are pure CSS on the compositor, costing no
main-thread time. Everything collapses under `prefers-reduced-motion` — drifting gradients are
genuinely unpleasant for people with vestibular disorders, not merely distracting.

**The demo section is real.** `src/lib/settle.ts` is the same greedy two-heap simplification the
backend runs, ported to the browser. Add an expense on the landing page and the balances and
payment plan actually recompute — including the live ledger check, which must read `₹0.00` no
matter what you type. The page cannot claim a result the algorithm does not produce.

**Performance.** The dashboard and auth screens are lazy-loaded, so a first-time visitor on the
marketing page does not download the app shell before seeing the hero.

```
dist/index.html           1.65 kB │ gzip   0.78 kB
dist/assets/index.css    37.69 kB │ gzip   7.21 kB
dist/assets/Login.js      3.18 kB │ gzip   1.32 kB
dist/assets/Dashboard.js 14.95 kB │ gzip   4.29 kB
dist/assets/index.js    375.57 kB │ gzip 119.54 kB
```

**Verification.** `npm run visual-check` drives a real browser against the production build:
screenshots in both themes and at mobile width, asserts every section renders, fails on any console
error, and exercises the demo and FAQ accordion to prove they are not just decorative. It needs a
local Chrome or Edge:

```powershell
cd frontend
npm run build
npx vite preview --port 4173     # in one terminal
npm run visual-check             # in another
```

---

## What I would do next

Things deliberately left out, and why:

- **Multi-currency trips.** Needs historical FX rates stored per expense, since converting at read
  time makes past balances shift around. Real work, not a config flag.
- **Partial settlements against a specific plan transfer.** The data model supports it; the UX
  question of what happens to the rest of the plan is the hard part.
- **Group-level sharding of the lock.** Only worth it if a single group ever became hot enough to
  matter, which for a flat-share app it will not.
- **Push notifications.** The async fan-out, threading and failure isolation are built and tested;
  only the delivery provider is stubbed.

---

## Stack

**Backend** — Java 17 · Spring Boot 3.4 · Spring Data JPA / Hibernate · PostgreSQL · Redis ·
Spring Security (JWT) · JUnit 5

**Frontend** — React 18 · TypeScript · Vite · Tailwind CSS · Framer Motion

**Ops** — Docker · GitHub Actions
