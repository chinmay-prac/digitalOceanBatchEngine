package com.batchengine.model;

import java.util.List;
import java.util.UUID;

public record BatchSnapshot(
        UUID batchId,
        BatchStatus status,
        int total,
        int completed,
        int failed,
        int finished,
        List<PromptResult> results) {
}
