package com.batchengine.service;

import com.batchengine.inference.InferenceExecutionException;
import com.batchengine.inference.InferenceOutcome;
import com.batchengine.inference.RetryableInferenceService;
import com.batchengine.model.BatchContext;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);
    private final RetryableInferenceService inferenceService;

    public BatchProcessor(RetryableInferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    public void process(BatchContext context, String prompt, int index) {
        long started = System.nanoTime();
        context.markProcessing(index);
        try {
            InferenceOutcome outcome = inferenceService.infer(prompt, index);
            context.complete(new PromptResult(
                    index, PromptStatus.COMPLETED, outcome.output(), outcome.attempts(), null));
            log.info("batchId={} promptIndex={} attempts={} status=COMPLETED durationMs={}",
                    context.batchId(), index, outcome.attempts(), elapsedMillis(started));
        } catch (InferenceExecutionException exception) {
            context.complete(new PromptResult(
                    index, PromptStatus.FAILED, null, exception.getAttempts(), exception.getMessage()));
            log.error("batchId={} promptIndex={} attempts={} status=FAILED durationMs={} error={}",
                    context.batchId(), index, exception.getAttempts(),
                    elapsedMillis(started), exception.getMessage());
        } catch (RuntimeException exception) {
            context.complete(new PromptResult(
                    index, PromptStatus.FAILED, null, 1, "Unexpected inference failure"));
            log.error("batchId={} promptIndex={} attempts=1 status=FAILED durationMs={}",
                    context.batchId(), index, elapsedMillis(started), exception);
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
