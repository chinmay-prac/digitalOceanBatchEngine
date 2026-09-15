package com.batchengine.inference;

import com.batchengine.config.InferenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetryableInferenceService {

    private static final Logger log = LoggerFactory.getLogger(RetryableInferenceService.class);

    private final InferenceClient client;
    private final Sleeper sleeper;
    private final InferenceProperties properties;

    public RetryableInferenceService(
            InferenceClient client, Sleeper sleeper, InferenceProperties properties) {
        this.client = client;
        this.sleeper = sleeper;
        this.properties = properties;
    }

    public InferenceOutcome infer(String prompt, int promptIndex) {
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                return new InferenceOutcome(client.infer(prompt, promptIndex, attempt), attempt);
            } catch (RateLimitException exception) {
                if (attempt == properties.getMaxAttempts()) {
                    throw new InferenceExecutionException(
                            "Rate limit attempts exhausted", attempt, exception);
                }
                long delay = backoffFor(attempt);
                log.warn("promptIndex={} attempt={} retryDelayMs={} status=RATE_LIMITED",
                        promptIndex, attempt, delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InferenceExecutionException(
                            "Retry interrupted", attempt, interrupted);
                }
            } catch (RuntimeException exception) {
                throw new InferenceExecutionException(
                        "Non-retryable inference failure", attempt, exception);
            }
        }
        throw new IllegalStateException("retry loop terminated unexpectedly");
    }

    private long backoffFor(int failedAttempt) {
        try {
            return Math.multiplyExact(
                    properties.getInitialBackoffMs(), 1L << (failedAttempt - 1));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
