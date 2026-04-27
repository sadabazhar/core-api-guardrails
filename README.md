# Core API Guardrails — Spring Boot Microservice

A high-performance Spring Boot microservice that acts as a central API gateway with a built-in Redis-backed guardrail system for a social platform with human users and AI bots. It handles concurrent requests, distributed state management, virality scoring, and smart notification batching.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Phase 1 — Core API](#phase-1--core-api)
- [Phase 2 — Redis Virality Engine & Atomic Guardrails](#phase-2--redis-virality-engine--atomic-guardrails)
- [Phase 3 — Smart Notification Engine](#phase-3--smart-notification-engine)
- [Phase 4 — Load Testing & Results](#phase-4--load-testing--results)
- [Thread Safety & Concurrency Design](#thread-safety--concurrency-design)
- [Redis Key Reference](#redis-key-reference)
- [Error Handling](#error-handling)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 16 |
| Cache / State | Redis 7 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Load Testing | k6 |
| Containerization | Docker & Docker Compose |

---

## Architecture Overview

```
Client
  │
  ▼
PostController
  │
  ├──► PostService / CommentService  ──► PostgreSQL (source of truth)
  │         │
  │         ├──► GuardrailService   ──► Redis (atomic locks & caps)
  │         ├──► ViralityService    ──► Redis (virality score)
  │         └──► NotificationService──► Redis (notification batching)
  │
  └──► GlobalExceptionHandler (unified error responses)
```

**Key design principle:** Redis acts as the gatekeeper. Database transactions are only committed *after* all Redis guardrails pass. On any failure, Redis state is rolled back manually to maintain consistency.

---

## Project Structure

```
src/
├── controller/
│   └── PostController.java
├── service/
│   ├── PostService.java
│   ├── CommentService.java
│   ├── GuardrailService.java
│   ├── ViralityService.java
│   └── NotificationService.java
├── scheduler/
|    ├── NotificationScheduler.java
├── entity/
│   ├── User.java
│   ├── Bot.java
│   ├── Post.java
│   └── Comment.java
├── repository/
│   ├── UserRepository.java
│   ├── BotRepository.java
│   ├── PostRepository.java
│   └── CommentRepository.java
├── dto/
│   ├── request/   (PostRequest, CommentRequest)
│   └── response/  (PostResponse, CommentResponse, ErrorResponse)
├── exception/
|   ├── GlobalExceptionHandler
│   ├── BaseException.java
│   ├── ResourceNotFoundException.java
│   ├── GuardrailException.java
│   └── TooManyRequestsException.java
└── common/
    └── RedisKeys.java
    └── Interactiontype.java
resources/
├── application.yml
└── db/migration/
    ├── V1__initial_schema.sql
    ├── V2__seed_data.sql
    └── V3__add_nested_comments.sql
```

---

## Database Schema

### V1 — Initial Schema

```sql
users       (id, username, is_premium, created_at)
bots        (id, name, persona_description, created_at)
posts       (id, author_id, author_type, content, like_count, created_at)
comments    (id, post_id, author_id, author_type, content, depth_level, created_at)
```

- `author_type` is constrained to `'USER'` or `'BOT'` at the DB level via a `CHECK` constraint.
- `depth_level` is constrained between `0` and `20`.
- Comments have a cascading foreign key to posts (`ON DELETE CASCADE`).

### V2 — Seed Data

Pre-seeds **10 users** and **10 bots** for API and load testing.

### V3 — Nested Comments

Adds `parent_comment_id` as a self-referencing foreign key to `comments`, enabling a threaded comment hierarchy up to 20 levels deep.

### Indexes

```sql
idx_posts_author       ON posts(author_id, author_type)
idx_comments_post_id   ON comments(post_id)
idx_comments_author    ON comments(author_id, author_type)
idx_comments_depth     ON comments(depth_level)
idx_comments_parent    ON comments(parent_comment_id)
```

---

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven or Gradle

### 1. Start Infrastructure

```bash
docker compose up -d
```

This spins up:
- **PostgreSQL** on `localhost:5432` (DB: `grid07`, User: `grid07_user`)
- **Redis** on `localhost:6379`

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

Flyway will automatically apply all migrations on startup. The server starts on **port 8080**.

### 3. Verify

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"content": "Hello World", "authorId": 1, "authorType": "USER"}'
```

---

## API Reference

### POST `/api/posts`

Create a new post authored by a user or bot.

**Request Body:**
```json
{
  "content": "Post content here",
  "authorId": 1,
  "authorType": "USER"
}
```

**Response:** `201 Created`
```json
{
  "id": 1,
  "content": "Post content here",
  "authorId": 1,
  "authorType": "USER",
  "likeCount": 0,
  "createdAt": "2025-07-15T10:30:00"
}
```

---

### POST `/api/posts/{postId}/like`

Like a post. Increments `like_count` in PostgreSQL and adds **+20 points** to the virality score in Redis.

**Response:** `200 OK` — returns the updated post.

---

### POST `/api/posts/{postId}/comments`

Add a comment (or a nested reply) to a post.

**Request Body:**
```json
{
  "content": "My comment",
  "authorId": 3,
  "authorType": "BOT",
  "parentCommentId": null
}
```

Set `parentCommentId` to an existing comment's ID to create a nested reply.

**Response:** `201 Created`
```json
{
  "id": 5,
  "postId": 1,
  "authorId": 3,
  "authorType": "BOT",
  "content": "My comment",
  "depthLevel": 0,
  "createdAt": "2025-07-15T10:31:00"
}
```

**Guardrail Errors (for bot authors):**

| Scenario | HTTP Status | Message |
|---|---|---|
| Post has 100+ bot replies | `429 Too Many Requests` | Post X has reached the maximum of 100 bot replies |
| Bot on cooldown for this user | `429 Too Many Requests` | Bot X is on cooldown for this user |
| Thread depth > 20 | `422` / `GuardrailException` | Max depth 20 exceeded |
| Author not found | `404 Not Found` | USER/BOT not found: X |
| Post not found | `404 Not Found` | Post not found: X |

---

## Phase 1 — Core API

The `PostController` exposes all three endpoints and delegates to `PostService` and `CommentService`. Author existence is validated before any entity is created — if the referenced `authorId` doesn't exist in the `users` or `bots` table (based on `authorType`), the request is rejected with a `404`.

`like_count` is incremented using a direct JPQL `UPDATE` query (`postRepository.incrementLikeCount`) to avoid a read-modify-write cycle and ensure atomicity at the DB level.

---

## Phase 2 — Redis Virality Engine & Atomic Guardrails

### Virality Score

Every interaction atomically increments a Redis key using `INCR`:

| Interaction | Points | Redis Key |
|---|---|---|
| Bot Reply | +1 | `post:{id}:virality_score` |
| Human Like | +20 | `post:{id}:virality_score` |
| Human Comment | +50 | `post:{id}:virality_score` |

```java
// ViralityService
redisTemplate.opsForValue().increment(key, points);
```

### Guardrail 1 — Horizontal Cap (100 bot replies per post)

**Redis Key:** `post:{id}:bot_count`

Implemented using a **Lua script** executed atomically on the Redis server:

```lua
local current = redis.call('GET', KEYS[1])
current = tonumber(current) or 0
if current >= tonumber(ARGV[1]) then return 0 end
redis.call('INCR', KEYS[1])
return 1
```

The Lua script guarantees that the check-and-increment is **atomic** — no two concurrent requests can both read `99` and both increment to `100`, which would result in `101`. The script returns `0` (blocked) or `1` (allowed) as a single indivisible operation.

### Guardrail 2 — Vertical Cap (max 20 depth levels)

Enforced in two places:
1. In `CommentService`, by computing `depthLevel = parent.getDepthLevel() + 1` and checking `depthLevel <= MAX_DEPTH`.
2. At the database level via a `CHECK (depth_level >= 0 AND depth_level <= 20)` constraint.

### Guardrail 3 — Cooldown Cap (bot → human, once per 10 minutes)

**Redis Key:** `cooldown:bot_{botId}:human_{humanId}`

Uses `SET NX` (set-if-absent) with a 10-minute TTL:

```java
redisTemplate.opsForValue()
    .setIfAbsent(key, "1", COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);
```

`SET NX` is atomic at the Redis level. Only the first call sets the key; all subsequent calls within the TTL window return `false`, blocking the bot.

The target human is resolved as follows:
- If the bot is replying to a **comment**, the cooldown applies to that comment's human author.
- If the bot is replying to a **top-level post**, the cooldown applies to the post's human author.
- If both the post and parent comment are bot-owned, no cooldown is needed.

### Redis Rollback on Failure

If the database `save()` fails after Redis guardrails have already been updated, the `CommentService` catches the exception and manually rolls back:

```java
// ROLLBACK BOT COUNT
if (botCountIncremented) {
    redisTemplate.opsForValue().decrement(RedisKeys.botCount(postId));
}

// ROLLBACK COOLDOWN
if (cooldownAcquired && targetHumanId != null) {
    redisTemplate.delete(RedisKeys.cooldown(request.authorId(), targetHumanId));
}
```

This keeps Redis and PostgreSQL consistent even in failure scenarios.

---

## Phase 3 — Smart Notification Engine

Bot interactions use a **two-tier notification system** to prevent notification spam:

### Tier 1 — Immediate Notification (First interaction)

**Redis Key:** `notif:cooldown:{userId}` (TTL: 15 minutes)

On the first bot interaction, the notification is sent immediately (logged to console) and a 15-minute cooldown key is set using `SET NX`.

### Tier 2 — Batched Notification (Repeat interactions within cooldown)

**Redis Key:** `user:{userId}:pending_notifs` (Redis List)

If the cooldown key already exists, the notification message is pushed to a Redis List:
```
RPUSH user:{id}:pending_notifs "Bot X replied to your post"
```

Active users with pending notifications are tracked in a Redis Set:
```
SADD active_users {userId}
```

### CRON Sweeper (`@Scheduled`)

Runs every **5 minutes** (simulating the 15-minute production cadence). For each user in the `active_users` set:
1. Pops all messages from `user:{id}:pending_notifs`.
2. Counts unique bots from the messages.
3. Logs a summarized notification: `"Summarized Push Notification: Bot X and [N] others interacted with your posts."`
4. Clears the user's pending notification list.

**Sample console output:**
```
Push Notification Sent to User 1: Bot 5 replied to your post
Notification queued for user 1: Bot 2 replied to your post
Summarised Push Notification: Bot 2 and 3 others interacted with your posts.
No pending notifications to process
```

The `NotificationService` wraps all Redis operations in a try-catch to ensure notification failures **never break** the main comment creation flow.

---

## Phase 4 — Load Testing & Results

Load tests are written in **k6** (`load-test.js`) and cover four concurrent scenarios:

| Scenario | Executor | Rate / VUs | Duration |
|---|---|---|---|
| `horizontal_spam` | constant-arrival-rate | 200 req/s | 10s |
| `cooldown_spam` | constant-arrival-rate | 50 req/s | 10s |
| `depth_test` | per-vu-iterations | 1 VU, 21 iterations | once |
| `like_posts` | constant-vus | 10 VUs | 10s |


### Run the Load Test

```bash
k6 run load-test.js
```

### Results

```
✓ checks_succeeded    100.00%  (5468/5468)
✓ http_req_duration   p(95) = 253.76ms  (threshold: <1500ms)
✓ http_req_failed     rate  = 80.90%    (threshold: <0.90 — 429s are expected)
```

All 5,468 checks passed. The 80.9% "failure" rate consists entirely of expected `429 Too Many Requests` responses from the guardrails.

### PostgreSQL Verification

```sql
SELECT COUNT(*) FROM comments WHERE post_id = 1;  -- cooldown post  → 10
SELECT COUNT(*) FROM comments WHERE post_id = 2;  -- horizontal cap → 100 (exactly)
SELECT MAX(depth_level) FROM comments WHERE post_id = 6; -- depth test → 20
```

The horizontal cap stopped at **exactly 100** bot replies under 200 req/s concurrent load — no overwrites, no race conditions.

### Redis Verification

```
GET post:1:bot_count  →  "10"
GET post:2:bot_count  →  "100"
```

---

## Thread Safety & Concurrency Design

The core of the concurrency guarantee is the **Lua script** used for the horizontal bot cap:

> **Why Lua?** Redis executes Lua scripts atomically — the entire script runs as a single unit on the server with no other command interleaving. This eliminates the TOCTOU (Time-of-Check to Time-of-Use) race condition that would occur with a naive `GET` → check → `INCR` sequence in application code.

For the **cooldown cap**, Redis's native `SET NX` (set-if-not-exists) is inherently atomic, making it safe for concurrent access without additional locking.

For **like counts**, the database-level `UPDATE ... SET like_count = like_count + 1 WHERE id = ?` is a single atomic SQL statement, avoiding any read-modify-write race at the JPA layer.

The application itself is **fully stateless** — no `HashMap`, `static` variables, or in-memory counters are used. All shared state lives exclusively in Redis or PostgreSQL, making the service horizontally scalable.

---

## Redis Key Reference

| Key Pattern | Type | Purpose | TTL |
|---|---|---|---|
| `post:{id}:virality_score` | String | Cumulative virality score for a post | None |
| `post:{id}:bot_count` | String | Number of bot replies on a post | None |
| `cooldown:bot_{botId}:human_{humanId}` | String | Bot-to-human interaction cooldown | 10 min |
| `notif:cooldown:{userId}` | String | Per-user notification cooldown | 15 min |
| `user:{id}:pending_notifs` | List | Queued notification messages | 1 hour |
| `active_users` | Set | Users with pending notifications | 1 hour |

---

## Error Handling

All errors are handled by `GlobalExceptionHandler` and return a consistent JSON structure:

```json
{
  "timestamp": "2025-07-15T10:30:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Post 5 has reached the maximum of 100 bot replies",
  "path": "/api/posts/5/comments"
}
```

Custom exceptions (`ResourceNotFoundException`, `GuardrailException`, `TooManyRequestsException`) extend `BaseException` and carry their own HTTP status codes. All unhandled exceptions fall back to `500 Internal Server Error`.