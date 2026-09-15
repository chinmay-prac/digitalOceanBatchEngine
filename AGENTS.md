# Batch Inference Engine — Agent Instructions

## Objective

Build a Java 17 Spring Boot REST API that accepts either a raw JSON prompt array or a multipart UTF-8 `.txt` file with one non-blank prompt per line, acknowledges the batch immediately, processes prompts concurrently against a mock HTTP inference endpoint, retries actual HTTP 429 responses with sleep-based backoff, and aggregates successful results.

This is a ninety-minute coding, deployment, and design exercise followed by code review. Prefer a small, complete, tested implementation over production-scale infrastructure.

## Technology

- Java 17
- Spring Boot 3
- Maven and Maven Wrapper
- Spring Web
- Bean Validation
- Spring Actuator
- JUnit 5 and Mockito
- Docker
- GitHub Actions

## Architecture Decisions

- Use one Spring Boot application.
- Use a bounded `ThreadPoolTaskExecutor`; never create one thread per prompt.
- Return `202 Accepted` after registering and scheduling a batch; do not wait for inference completion.
- Hide inference behind an `InferenceClient` interface.
- Provide a deterministic mock HTTP controller and call it through an `InferenceClient` HTTP adapter that converts actual HTTP 429 responses into retryable failures.
- Retry only rate-limit failures. Use three total attempts by default with configurable sleep-based exponential backoff and attempt count.
- Inject a `Sleeper` abstraction so unit tests never perform real waits.
- Preserve prompt input order by assigning every prompt an index and ordering results by that index.
- Persist batches, prompts, states, attempts, outputs, and errors through Spring JDBC.
- Use PostgreSQL in deployment, H2 PostgreSQL mode locally, and Flyway as the only schema owner.
- Keep a thread-safe in-memory context only for coordination while a batch is actively executing.
- Use atomic counters and concurrent collections for shared mutable state.
- Permit partial batch failure after retries are exhausted; never discard an item silently.
- Treat the status API as an extension to implement only after the core path and retry tests pass.

## Explicit Non-Goals

Do not introduce any of the following unless the prompt is changed explicitly:

- Kafka or Flink
- JPA or Hibernate schema generation
- Distributed locks, leases, or multi-instance coordination
- Kubernetes
- Priority scheduling
- Idempotency keys
- Authentication or authorization
- A real model provider
- Automatic scaling infrastructure

## State Model

Batch states:

- `ACCEPTED`
- `PROCESSING`
- `COMPLETED`
- `PARTIALLY_FAILED`
- `FAILED`

Prompt result states:

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

## Correctness Invariants

1. Every accepted prompt is submitted to the bounded executor exactly once.
2. A 429 response is retried up to the configured maximum attempt count.
3. A retry sleeps for the configured backoff before the next attempt.
4. Non-rate-limit failures are not retried unless requirements explicitly say otherwise.
5. Each prompt reaches exactly one terminal state: `COMPLETED` or `FAILED`.
6. `finishedCount` equals `completedCount + failedCount`.
7. A batch becomes terminal only when `finishedCount == totalCount`.
8. Final results are returned in original prompt order regardless of completion order.
9. Concurrent updates must not use an ordinary mutable `ArrayList` or non-atomic counters without synchronization.
10. Interrupted sleeps restore the thread's interrupt flag.
11. Acknowledged batches and terminal results survive process restart.
12. Restart recovery terminally fails unfinished persisted prompts.

## Working Rules for Cursor

- Read `AGENTS.md`, `DESIGN.md`, and `IMPLEMENTATION_PLAN.md` before editing.
- State the plan section and files that will change before editing.
- Implement only one plan section at a time.
- Do not add dependencies without explaining their purpose.
- Do not silently expand scope.
- Run focused tests after each change, then run the full test suite after each vertical slice.
- After editing, report changed files, important decisions, commands run, test results, and incomplete items.
- If a request contradicts this file or `DESIGN.md`, identify the conflict before making changes.
- Never claim a test passed unless its command was executed successfully.
