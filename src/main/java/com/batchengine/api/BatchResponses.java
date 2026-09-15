package com.batchengine.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.BatchStatus;
import com.batchengine.model.PromptResult;
import java.util.List;
import java.util.UUID;

public final class BatchResponses {

    private BatchResponses() {
    }

    public record Submission(UUID batchId, BatchStatus status, int total) {
        static Submission from(BatchSnapshot snapshot) {
            return new Submission(snapshot.batchId(), snapshot.status(), snapshot.total());
        }
    }

    public record Status(
            UUID batchId,
            BatchStatus status,
            int total,
            int completed,
            int failed,
            int finished) {
        static Status from(BatchSnapshot snapshot) {
            return new Status(
                    snapshot.batchId(), snapshot.status(), snapshot.total(),
                    snapshot.completed(), snapshot.failed(), snapshot.finished());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Results(
            UUID batchId,
            BatchStatus status,
            int total,
            int completed,
            int failed,
            List<PromptResult> results) {
        static Results from(BatchSnapshot snapshot, boolean includeResults) {
            return new Results(
                    snapshot.batchId(), snapshot.status(), snapshot.total(),
                    snapshot.completed(), snapshot.failed(),
                    includeResults ? snapshot.results() : null);
        }
    }
}
