package com.batchengine.service;

import com.batchengine.inference.InferenceExecutionException;
import com.batchengine.inference.InferenceOutcome;
import com.batchengine.inference.RetryableInferenceService;
import com.batchengine.model.BatchContext;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import com.batchengine.store.JdbcBatchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);
    private final RetryableInferenceService inferenceService;
    private final JdbcBatchStore persistentStore;
    private final CompletionPersistenceCoordinator persistenceCoordinator;

    public BatchProcessor(
            RetryableInferenceService inferenceService,
            JdbcBatchStore persistentStore,
            CompletionPersistenceCoordinator persistenceCoordinator) {
        this.inferenceService = inferenceService;
        this.persistentStore = persistentStore;
        this.persistenceCoordinator = persistenceCoordinator;
    }

    public void process(BatchContext context, String prompt, int index) {
        long started = System.nanoTime();
        context.markProcessing(index);
        try {
            persistentStore.markProcessing(context.batchId(), index);
        } catch (DataAccessException exception) {
            log.error("batchId={} promptIndex={} could not persist PROCESSING state",
                    context.batchId(), index, exception);
        }
        PromptResult result;
        try {
            InferenceOutcome outcome = inferenceService.infer(prompt, index);
            result = new PromptResult(
                    index, PromptStatus.COMPLETED, outcome.output(), outcome.attempts(), null);
        } catch (InferenceExecutionException exception) {
            result = new PromptResult(
                    index, PromptStatus.FAILED, null,
                    exception.getAttempts(), exception.getMessage());
        } catch (RuntimeException exception) {
            result = new PromptResult(
                    index, PromptStatus.FAILED, null, 1, "Unexpected inference failure");
            log.error("batchId={} promptIndex={} unexpected inference failure",
                    context.batchId(), index, exception);
        }

        persistenceCoordinator.persist(context.batchId(), result);
        context.complete(result);
        if (result.status() == PromptStatus.COMPLETED) {
            log.info("batchId={} promptIndex={} attempts={} status={} durationMs={}",
                    context.batchId(), index, result.attempts(),
                    result.status(), elapsedMillis(started));
        } else {
            log.error("batchId={} promptIndex={} attempts={} status={} durationMs={} error={}",
                    context.batchId(), index, result.attempts(), result.status(),
                    elapsedMillis(started), result.error());
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
