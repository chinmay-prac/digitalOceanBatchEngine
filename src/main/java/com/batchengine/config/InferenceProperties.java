package com.batchengine.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "inference")
public class InferenceProperties {

    @Min(1)
    private int maxBatchSize = 1000;
    @Min(1)
    private int workerCount = 8;
    @Min(1)
    private int queueCapacity = 2000;
    @Min(1)
    private int maxAttempts = 3;
    @Min(0)
    private long initialBackoffMs = 100;
    @Min(0)
    private int mockRateLimitEvery = 4;

    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int value) { this.maxBatchSize = value; }
    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int value) { this.workerCount = value; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { this.queueCapacity = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public void setInitialBackoffMs(long value) { this.initialBackoffMs = value; }
    public int getMockRateLimitEvery() { return mockRateLimitEvery; }
    public void setMockRateLimitEvery(int value) { this.mockRateLimitEvery = value; }
}
