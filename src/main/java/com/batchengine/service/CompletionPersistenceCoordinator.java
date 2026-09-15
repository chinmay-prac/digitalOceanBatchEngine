package com.batchengine.service;

import com.batchengine.config.InferenceProperties;
import com.batchengine.inference.Sleeper;
import com.batchengine.model.PromptResult;
import com.batchengine.store.JdbcBatchStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CompletionPersistenceCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(CompletionPersistenceCoordinator.class);

    private final JdbcBatchStore store;
    private final Sleeper sleeper;
    private final InferenceProperties properties;
    private final ConcurrentLinkedQueue<PendingCompletion> pending =
            new ConcurrentLinkedQueue<>();

    public CompletionPersistenceCoordinator(
            JdbcBatchStore store,
            Sleeper sleeper,
            InferenceProperties properties) {
        this.store = store;
        this.sleeper = sleeper;
        this.properties = properties;
    }

    public void persist(UUID batchId, PromptResult result) {
        if (!persistNow(batchId, result)) {
            pending.offer(new PendingCompletion(batchId, result));
            log.error("batchId={} promptIndex={} persistenceStatus=QUEUED_FOR_RETRY",
                    batchId, result.index());
        }
    }

    @Scheduled(fixedDelayString = "${inference.persistence-retry-ms:1000}")
    void retryPending() {
        int currentSize = pending.size();
        for (int index = 0; index < currentSize; index++) {
            PendingCompletion completion = pending.poll();
            if (completion == null) {
                return;
            }
            if (!persistNow(completion.batchId(), completion.result())) {
                pending.offer(completion);
            }
        }
    }

    private boolean persistNow(UUID batchId, PromptResult result) {
        for (int attempt = 1; attempt <= properties.getPersistenceMaxAttempts(); attempt++) {
            try {
                store.complete(batchId, result);
                return true;
            } catch (DataAccessException exception) {
                log.warn(
                        "batchId={} promptIndex={} persistenceAttempt={} status=FAILED error={}",
                        batchId, result.index(), attempt, exception.getMessage());
                if (attempt < properties.getPersistenceMaxAttempts() && !sleepBeforeRetry()) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean sleepBeforeRetry() {
        try {
            sleeper.sleep(properties.getPersistenceRetryMs());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record PendingCompletion(UUID batchId, PromptResult result) {
    }
}
