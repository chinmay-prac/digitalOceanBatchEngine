package com.batchengine.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.BatchStatus;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JdbcBatchStoreTest {

    @Autowired
    private JdbcBatchStore store;

    @Test
    void persistsDuplicatePromptsAsIndependentIndexedResults() {
        UUID batchId = UUID.randomUUID();
        store.create(batchId, List.of("same", "same"));
        store.markProcessing(batchId, 0);
        store.markProcessing(batchId, 1);

        assertThat(store.complete(batchId,
                new PromptResult(1, PromptStatus.FAILED, null, 3, "rate limited"))).isTrue();
        assertThat(store.complete(batchId,
                new PromptResult(0, PromptStatus.COMPLETED, "ok", 1, null))).isTrue();

        BatchSnapshot snapshot = store.find(batchId).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(BatchStatus.PARTIALLY_FAILED);
        assertThat(snapshot.completed()).isEqualTo(1);
        assertThat(snapshot.failed()).isEqualTo(1);
        assertThat(snapshot.finished()).isEqualTo(2);
        assertThat(snapshot.results()).extracting(PromptResult::index).containsExactly(0, 1);
    }

    @Test
    void restartRecoveryTerminallyFailsUnfinishedPrompts() {
        UUID batchId = UUID.randomUUID();
        store.create(batchId, List.of("one", "two"));
        store.markProcessing(batchId, 0);

        assertThat(store.recoverIncomplete()).isGreaterThanOrEqualTo(1);

        BatchSnapshot snapshot = store.find(batchId).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(snapshot.failed()).isEqualTo(2);
        assertThat(snapshot.finished()).isEqualTo(2);
        assertThat(snapshot.results())
                .allSatisfy(result -> assertThat(result.error())
                        .isEqualTo("Application restarted before completion"));
    }
}
