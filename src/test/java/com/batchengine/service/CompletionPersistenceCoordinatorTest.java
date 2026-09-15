package com.batchengine.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batchengine.config.InferenceProperties;
import com.batchengine.inference.Sleeper;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import com.batchengine.store.JdbcBatchStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

class CompletionPersistenceCoordinatorTest {

    @Test
    void transientDatabaseFailuresRetryWithoutRealSleeping() throws Exception {
        JdbcBatchStore store = mock(JdbcBatchStore.class);
        Sleeper sleeper = mock(Sleeper.class);
        InferenceProperties properties = new InferenceProperties();
        properties.setPersistenceMaxAttempts(3);
        properties.setPersistenceRetryMs(50);
        UUID batchId = UUID.randomUUID();
        PromptResult result =
                new PromptResult(0, PromptStatus.COMPLETED, "output", 1, null);
        when(store.complete(batchId, result))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenReturn(true);

        new CompletionPersistenceCoordinator(store, sleeper, properties)
                .persist(batchId, result);

        verify(store, times(3)).complete(batchId, result);
        verify(sleeper, times(2)).sleep(50);
    }

    @Test
    void exhaustedCompletionIsQueuedForBackgroundReconciliation() {
        JdbcBatchStore store = mock(JdbcBatchStore.class);
        Sleeper sleeper = milliseconds -> { };
        InferenceProperties properties = new InferenceProperties();
        properties.setPersistenceMaxAttempts(3);
        properties.setPersistenceRetryMs(0);
        UUID batchId = UUID.randomUUID();
        PromptResult result =
                new PromptResult(0, PromptStatus.COMPLETED, "output", 1, null);
        when(store.complete(batchId, result))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenThrow(new TransientDataAccessResourceException("temporary"))
                .thenReturn(true);
        CompletionPersistenceCoordinator coordinator =
                new CompletionPersistenceCoordinator(store, sleeper, properties);

        coordinator.persist(batchId, result);
        coordinator.retryPending();

        verify(store, times(4)).complete(batchId, result);
    }
}
