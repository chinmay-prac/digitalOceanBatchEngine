package com.batchengine.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class BatchContext {

    private final UUID batchId;
    private final int total;
    private final AtomicReference<BatchStatus> status = new AtomicReference<>(BatchStatus.ACCEPTED);
    private final AtomicReferenceArray<PromptStatus> promptStatuses;
    private final ConcurrentHashMap<Integer, PromptResult> results = new ConcurrentHashMap<>();
    private int completed;
    private int failed;
    private int finished;

    public BatchContext(UUID batchId, int total) {
        if (total < 1) {
            throw new IllegalArgumentException("total must be positive");
        }
        this.batchId = batchId;
        this.total = total;
        this.promptStatuses = new AtomicReferenceArray<>(total);
        for (int index = 0; index < total; index++) {
            promptStatuses.set(index, PromptStatus.PENDING);
        }
    }

    public UUID batchId() {
        return batchId;
    }

    public void markProcessing(int index) {
        promptStatuses.compareAndSet(index, PromptStatus.PENDING, PromptStatus.PROCESSING);
        status.compareAndSet(BatchStatus.ACCEPTED, BatchStatus.PROCESSING);
    }

    public synchronized boolean complete(PromptResult result) {
        int index = result.index();
        if (result.status() != PromptStatus.COMPLETED && result.status() != PromptStatus.FAILED) {
            throw new IllegalArgumentException("result must be terminal");
        }
        if (!promptStatuses.compareAndSet(index, PromptStatus.PROCESSING, result.status())) {
            return false;
        }

        results.put(index, result);
        if (result.status() == PromptStatus.COMPLETED) {
            completed++;
        } else {
            failed++;
        }
        finished++;

        if (finished == total) {
            status.set(completed == total
                    ? BatchStatus.COMPLETED
                    : completed == 0 ? BatchStatus.FAILED : BatchStatus.PARTIALLY_FAILED);
        }
        return true;
    }

    public synchronized BatchSnapshot snapshot() {
        List<PromptResult> ordered = new ArrayList<>(results.values());
        ordered.sort(Comparator.comparingInt(PromptResult::index));
        return new BatchSnapshot(
                batchId, status.get(), total, completed, failed, finished, List.copyOf(ordered));
    }
}
