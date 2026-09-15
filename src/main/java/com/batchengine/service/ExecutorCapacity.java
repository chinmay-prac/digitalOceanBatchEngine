package com.batchengine.service;

import com.batchengine.config.InferenceProperties;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

@Component
public class ExecutorCapacity {

    private final Semaphore permits;

    public ExecutorCapacity(InferenceProperties properties) {
        this.permits = new Semaphore(
                properties.getWorkerCount() + properties.getQueueCapacity(), true);
    }

    public boolean tryReserve(int taskCount) {
        return permits.tryAcquire(taskCount);
    }

    public void release() {
        permits.release();
    }

    public void release(int taskCount) {
        permits.release(taskCount);
    }
}
