package com.batchengine.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class BatchContextTest {

    @Test
    void concurrentTerminalUpdatesAreCountedOnceAndOrdered() throws Exception {
        int total = 200;
        BatchContext context = new BatchContext(UUID.randomUUID(), total);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = total - 1; index >= 0; index--) {
                int promptIndex = index;
                futures.add(executor.submit(() -> {
                    context.markProcessing(promptIndex);
                    context.complete(new PromptResult(
                            promptIndex, PromptStatus.COMPLETED,
                            "result-" + promptIndex, 1, null));
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        BatchSnapshot snapshot = context.snapshot();
        assertThat(snapshot.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(snapshot.completed()).isEqualTo(total);
        assertThat(snapshot.failed()).isZero();
        assertThat(snapshot.finished()).isEqualTo(total);
        assertThat(snapshot.results()).extracting(PromptResult::index)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, total).boxed().toList());
    }

    @Test
    void duplicateCompletionDoesNotDoubleCount() {
        BatchContext context = new BatchContext(UUID.randomUUID(), 1);
        context.markProcessing(0);
        PromptResult result = new PromptResult(0, PromptStatus.COMPLETED, "ok", 1, null);

        assertThat(context.complete(result)).isTrue();
        assertThat(context.complete(result)).isFalse();
        assertThat(context.snapshot().finished()).isEqualTo(1);
    }
}
