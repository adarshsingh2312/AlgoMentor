# Day 3: Codeforces Sync Pipeline, Idempotent Upsert & Scheduler

This document covers the core architectural decisions, data pipeline patterns, and interview concepts implemented on Day 3.

---

## 1. Rate Limiting: Proactive Throttling vs. Reactive Backoff

### Q: What is the difference between Throttling and Retry/Backoff?
- **Throttling (Proactive):**
  - Always active, before every HTTP request.
  - Ensures we do not exceed the third-party rate limit (Codeforces enforces a limit of 1 request every 2 seconds).
  - Implemented using a synchronized method and `AtomicLong lastCallAt` measuring elapsed time between successive requests.
- **Retry with Exponential Backoff (Reactive):**
  - Triggers only when a request fails due to transient issues (network timeouts, HTTP 429 Too Many Requests, or HTTP 5xx Server Errors).
  - Waits progressively longer before retrying (e.g., 1s, then 2s, then 4s).
  - Prevents the "Thundering Herd" problem by giving the upstream server breathing room to recover.
- **Summary:** You need both: Throttling keeps you within limits during normal operation; Backoff prevents cascading failures when the remote service is struggling.

---

## 2. Spring Transaction Proxy Gotcha: Why Batch Beans?

### Q: Why can't a `@Transactional` batch method be called within the same service?
- **Spring Proxy Mechanism:** Spring manages transactions using CGLIB/JDK dynamic proxies. When an external caller invokes a method on a bean, it hits the proxy, which begins and commits the database transaction.
- **The Self-Invocation Bug:** If `UpsertService` calls `this.processBatch()`, the call bypasses the Spring proxy. The `@Transactional` annotation on `processBatch` is completely ignored, and the entire sync would run either without a transaction or in a single monolithic transaction.
- **Solution:** We extracted `ProblemBatchBean` and `SubmissionBatchBean` into separate `@Service` components. Calls from `UpsertService` cross the Spring proxy boundary, ensuring every 500-item chunk commits in its own distinct transaction.
- **Benefit:** If batch #4 fails due to bad data, batches #1, #2, and #3 are already safely committed in PostgreSQL.

---

## 3. Database Optimization: In-Memory Maps vs. N+1 Queries

### Q: How do we prevent 10,000 queries when syncing 10,000 problems?
- **The Bad Approach:** Querying the database `SELECT * FROM problems WHERE contest_id = ? AND index = ?` for every item in the incoming payload causes 10,000+ database queries (the classic N+1 problem).
- **Our Optimized Approach:**
  1. Fetch all existing problems with tags in a single database query using `LEFT JOIN FETCH p.tags`.
  2. Index them in memory: `Map<String, Problem> problemsByKey` (key: `contestId + ":" + index`).
  3. Fetch all existing tags once into `Map<String, Tag> tagsByName`.
  4. Perform $O(1)$ in-memory lookups for every problem and tag in the sync.
- **Submission Checking:** For user submissions, we collect up to 500 candidate IDs per page and fetch existing IDs with a single `WHERE cf_submission_id IN (:ids)` query.

---

## 4. Smart Pagination: Stop-at-Known Strategy

### Q: How does submission sync avoid pulling years of historical submissions every time?
- **Codeforces Ordering:** The Codeforces API returns submissions sorted **newest-first** (descending by submission time).
- **Stop Condition:** We fetch submissions in pages of 500. If an entire page yields **zero new insertions** (`submissionsInserted == 0`), we know all older submissions were already saved during previous sync runs.
- **Result:** The loop halts immediately. For an active user, sync finishes after inspecting only the first page (1 network call) instead of paginating through 3,000+ past submissions.

---

## 5. Idempotent Upsert & Concurrency Handling

### Q: What happens if two syncs run at the exact same time?
- **Idempotency:** Re-running the sync multiple times produces the exact same database state without duplicate rows (`problemsInserted = 0`, `submissionsSkipped = N`).
- **Race Conditions:** If two concurrent sync jobs attempt to insert the same new problem simultaneously, one will succeed and the second will hit PostgreSQL's unique constraint (`uk_problem_contest_index`).
- **Design Decision:** The database unique constraint is our ultimate source of truth. We do not introduce heavy distributed locks (e.g. Redlock or row locks) because sync operations are scheduled hours apart and collisions are rare.

---

## 6. Background Scheduling & Resilience

### Q: How is the scheduler designed for production resilience?
- **Master Switch (`app.sync.enabled=true`):** Can disable all cron jobs in testing or staging via environment variables without code modifications.
- **Isolated User Syncs:** In `SyncScheduler`, we iterate over users having `cfHandle != null`. Each user sync is enclosed in its own `try-catch` block. If User A's sync fails (e.g. handle renamed or Codeforces API error), the scheduler logs the error and continues to User B and User C without failing the entire cron run.
- **Summary Metrics:** Every sync execution logs one concise line with elapsed time (ms), records inserted, and records updated/skipped.

---

## 7. API Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/admin/sync/problemset` | `ROLE_ADMIN` | Synchronously syncs all Codeforces problems |
| `POST` | `/api/me/sync` | `ROLE_USER` / `ROLE_ADMIN` | Synchronously syncs logged-in user's submissions |
| `PUT` | `/api/me/cf-handle` | Authenticated | Links and validates a Codeforces handle |
