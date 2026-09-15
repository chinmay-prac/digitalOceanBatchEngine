# Ninety-Minute Implementation and Deployment Plan

## Operating Method

- Keep one Cursor Agent conversation through the first complete vertical slice.
- Treat `AGENTS.md`, `DESIGN.md`, and this plan as authoritative.
- Execute one section at a time.
- Create a Git commit after every completed, passing vertical slice.
- If Cursor becomes confused, start a new conversation and instruct it to read these three files, inspect the Git diff, and summarize the next unfinished section before editing.

## Time Budget

| Time | Outcome |
| --- | --- |
| 0–10 min | Freeze scope and verify the existing Spring Boot scaffold |
| 10–40 min | TXT upload through concurrent mock inference and aggregation |
| 40–60 min | 429 retry/backoff, validation, and overload handling |
| 60–72 min | Focused automated tests |
| 72–82 min | Actuator, README, Docker, and deployment configuration |
| 82–90 min | Full verification and deployment smoke test |

## Section 0 — Clarify and Freeze Scope

### Questions

1. Implement the mock inference endpoint through an injectable in-process client.
2. Accept a multipart UTF-8 `.txt` upload with one non-blank prompt per line.
3. Use three total attempts with exponential backoff.
4. After attempts are exhausted, mark that prompt failed and allow the batch to finish.
5. Retrieve aggregated output as JSON through the results endpoint.
6. Preserve input order as a deterministic, low-cost implementation detail.
7. Keep state in memory for this exercise. Defer database persistence so deployment fits the time limit.

### Completion Criteria

- Assumptions in `DESIGN.md` match interviewer answers.
- No unresolved correctness requirement blocks implementation.
- Core functionality is separated from the optional status endpoint.

### Cursor Prompt

> Read `AGENTS.md`, `DESIGN.md`, and `IMPLEMENTATION_PLAN.md`. Do not write code. Review only for contradictions, unsafe concurrency, missing terminal behavior, and work infeasible within ninety minutes. Separate blockers from optional production improvements. Do not add infrastructure.

## Section 1 — Spring Boot Scaffold

### Work

- Create Java 17 Spring Boot Maven project.
- Add Spring Web, Validation, Actuator, and test dependencies.
- Add Maven Wrapper.
- Add base package and application class.
- Add baseline `application.yml`.

### Verification

```bash
./mvnw test
```

### Completion Criteria

- Application compiles.
- Empty context test passes.
- No domain logic exists yet.

### Cursor Prompt

> Implement only Section 1. Create the minimal Java 17 Spring Boot Maven scaffold with Web, Validation, Actuator, and Test. Include the Maven wrapper. Do not add persistence or domain code. Run `./mvnw test` and report changed files and results.

## Section 2 — Models, Store, and Executor

### Work

- Add `BatchStatus` and `PromptStatus` enums.
- Add upload/response DTOs.
- Add `PromptResult` containing original index, status, output, attempts, and error.
- Add thread-safe `BatchContext`.
- Add `InMemoryBatchStore` backed by `ConcurrentHashMap`.
- Configure bounded `ThreadPoolTaskExecutor`.
- Bind typed configuration properties.

### Tests

- Batch context counts concurrent terminal updates correctly.
- Ordered result projection returns results by input index.
- Configuration binds expected defaults.

### Verification

```bash
./mvnw -Dtest=BatchContextTest test
./mvnw test
```

### Completion Criteria

- No ordinary unsynchronized collection receives concurrent writes.
- Pool and queue capacities are configurable.
- Batch terminal-state calculation is deterministic.

### Cursor Prompt

> Implement only Section 2. Add the state model, DTOs, thread-safe in-memory batch context/store, typed configuration, and a bounded `ThreadPoolTaskExecutor`. Preserve input ordering using prompt indexes. Do not add controllers, retry logic, or inference execution. Add focused tests and run them, then run the full suite.

## Section 3 — Core Vertical Slice

### Work

- Add `InferenceClient` interface.
- Add a simple successful mock client first.
- Add `BatchProcessor` for one prompt.
- Add `BatchService` to register and schedule a batch.
- Add `POST /api/v1/batches`.
- Return `202` immediately.
- Aggregate successful results.
- Add `GET /api/v1/batches/{batchId}/results` needed to observe completion.

### Tests

- Valid `.txt` upload returns `202` and a batch ID.
- Background tasks eventually complete.
- Results preserve input order even when completion order differs.
- Unknown batch returns `404`.

### Verification

```bash
./mvnw -Dtest=BatchControllerTest,BatchServiceTest test
./mvnw test
```

### Completion Criteria

- End-to-end happy path works.
- HTTP request does not block for inference completion.
- Every prompt reaches `COMPLETED`.
- No retry behavior exists yet.

### Cursor Prompt

> Implement only Section 3: one complete happy-path vertical slice from POST batch to bounded asynchronous processing and ordered result retrieval. Use a successful mock `InferenceClient`. Return 202 without waiting. Do not implement retry or the status extension yet. Add focused tests and run the full suite.

## Section 4 — Rate-Limit Retry

### Work

- Add `RateLimitException` or equivalent 429 representation.
- Add `Sleeper` interface and production implementation.
- Add `RetryableInferenceService`.
- Retry only 429 failures.
- Apply configurable exponential backoff.
- Restore interrupt status on interruption.
- Enhance mock client to produce deterministic 429 responses.

### Tests

- `429 → success` succeeds.
- `429 → 429 → success` sleeps with expected delays.
- Retry exhaustion fails only that prompt.
- Non-429 exception is not retried.
- Interrupted sleep restores interrupt status.
- A recovered prompt does not fail its batch.

### Verification

```bash
./mvnw -Dtest=RetryableInferenceServiceTest test
./mvnw test
```

### Completion Criteria

- Tests perform no real sleeping.
- Attempt counts are accurate.
- No accepted prompt disappears.
- Batch reaches the correct terminal state.

### Cursor Prompt

> Implement only Section 4. Add testable 429-only retry with configurable exponential sleep backoff, maximum attempts, and correct interruption handling. Inject `Sleeper`; tests must not perform real waits. Update the processor to record attempts and failures. Run focused tests and then the full suite.

## Section 5 — Validation and Error Handling

### Work

- Validate non-empty prompt list.
- Validate every prompt is non-blank.
- Enforce maximum batch size.
- Add consistent error responses.
- Handle executor rejection without acknowledging silently lost work.

### Tests

- Missing, empty, non-TXT, blank-line, and oversized uploads return `400`.
- Unknown batch returns `404`.
- Executor saturation has explicit behavior.

### Verification

```bash
./mvnw -Dtest=BatchValidationTest,GlobalExceptionHandlerTest test
./mvnw test
```

### Completion Criteria

- Invalid input never reaches the executor.
- Errors do not expose internal stack traces.
- Executor rejection cannot silently drop an acknowledged prompt.

### Cursor Prompt

> Implement only Section 5. Add request validation, maximum batch enforcement, consistent API errors, and explicit executor-rejection behavior. Do not add persistence or unrelated features. Add focused tests and run the full suite.

## Section 6 — Progress Endpoint Extension

### Work

- Add `GET /api/v1/batches/{batchId}`.
- Return status, total, completed, failed, and finished counts.
- Confirm progress remains coherent during concurrent updates.

### Tests

- Progress moves from accepted/processing to terminal.
- `finished == completed + failed`.
- Unknown batch returns `404`.

### Verification

```bash
./mvnw -Dtest=BatchStatusControllerTest test
./mvnw test
```

### Completion Criteria

- Core requirements and retry tests were already passing before this section began.
- Endpoint returns coherent snapshots.

### Cursor Prompt

> Core requirements are passing. Implement only the optional progress endpoint in Section 6. Return coherent atomic counters and batch state without exposing mutable internals. Add focused tests and run the full suite.

## Section 7 — Observability and Documentation

### Work

- Configure `/actuator/health`.
- Add structured, contextual logs without full prompt content.
- Add architecture diagram and concurrency explanation to README.
- Document build, test, run, API, configuration, trade-offs, and limitations.

### Verification

```bash
./mvnw spring-boot:run
curl --fail http://localhost:8080/actuator/health
```

### Completion Criteria

- Health endpoint returns success.
- Logs contain batch ID, prompt index, attempts, retry delay, status, and duration.
- README lets an evaluator run the application without assistance.

### Cursor Prompt

> Implement only Section 7. Add Actuator health configuration, contextual structured logs without prompt content, and a concise README covering setup, APIs, concurrency, retry, configuration, diagrams, assumptions, and production trade-offs. Run the application and verify health.

## Section 8 — Docker

### Work

- Add multi-stage Dockerfile.
- Add `.dockerignore`.
- Respect `${PORT:8080}` and bind to `0.0.0.0`.

### Verification

```bash
docker build -t batch-inference-engine .
docker run --rm -p 8080:8080 batch-inference-engine
curl --fail http://localhost:8080/actuator/health
```

### Completion Criteria

- Image builds.
- Container starts.
- Health endpoint is reachable.

### Cursor Prompt

> Implement only Section 8. Add a minimal multi-stage Dockerfile and `.dockerignore`. Build and run the image, then verify `/actuator/health`. Do not alter application behavior unless container startup requires it.

## Section 9 — GitHub Actions

### Work

- Add `.github/workflows/ci.yml`.
- Trigger on pushes and pull requests.
- Configure Java 17 with Maven caching.
- Run `./mvnw --batch-mode clean verify`.

### Verification

```bash
./mvnw --batch-mode clean verify
```

### Completion Criteria

- Workflow syntax is valid.
- Local equivalent passes.
- No credentials are committed.

### Cursor Prompt

> Implement only Section 9. Add a minimal GitHub Actions workflow for Java 17 that runs the Maven clean verify lifecycle on pushes and pull requests. Run the equivalent command locally and report the result.

## Section 10 — Final Review and Demonstration

### Work

- Freeze features.
- Run all verification.
- Review code against confirmed requirements.
- Prepare one demonstration request.
- Prepare explanations for concurrency, retry, aggregation, overload, and production evolution.

### Verification

```bash
./mvnw --batch-mode clean verify
docker build -t batch-inference-engine .
```

Smoke-test sequence:

```bash
curl -X POST http://localhost:8080/api/v1/batches \
  -F 'file=@prompts.txt;type=text/plain'

curl http://localhost:8080/api/v1/batches/{batchId}

curl http://localhost:8080/api/v1/batches/{batchId}/results
```

### Final Code-Review Questions

- Where is concurrency bounded?
- What prevents lost or duplicated counter updates?
- Why are results deterministic despite concurrent execution?
- Which errors are retryable?
- How is sleep tested without delaying tests?
- What happens when the executor queue is full?
- What happens when the application restarts?
- Why was Kafka not used?
- How would retry change if delays were minutes rather than milliseconds?
- How would this design evolve for multiple service replicas?

### Cursor Prompt

> Freeze features. Review the repository against `AGENTS.md` and `DESIGN.md`. Identify only correctness failures, missing confirmed requirements, concurrency races, test failures, and documentation contradictions. Do not add optional architecture. Run the complete verification commands and prepare a concise code-review summary.
