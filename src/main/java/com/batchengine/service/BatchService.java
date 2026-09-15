package com.batchengine.service;

import com.batchengine.exception.BatchCapacityException;
import com.batchengine.exception.BatchNotFoundException;
import com.batchengine.model.BatchContext;
import com.batchengine.model.BatchSnapshot;
import com.batchengine.store.InMemoryBatchStore;
import com.batchengine.store.JdbcBatchStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class BatchService {

    private final InMemoryBatchStore store;
    private final JdbcBatchStore persistentStore;
    private final BatchProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final Object admissionLock = new Object();

    public BatchService(
            InMemoryBatchStore store,
            JdbcBatchStore persistentStore,
            BatchProcessor processor,
            ThreadPoolTaskExecutor inferenceExecutor) {
        this.store = store;
        this.persistentStore = persistentStore;
        this.processor = processor;
        this.executor = inferenceExecutor;
    }

    public BatchSnapshot submit(List<String> prompts) {
        synchronized (admissionLock) {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            int availableWorkers = Math.max(0, pool.getMaximumPoolSize() - pool.getActiveCount());
            int available = availableWorkers + pool.getQueue().remainingCapacity();
            if (pool.isShutdown() || prompts.size() > available) {
                throw new BatchCapacityException();
            }

            BatchContext context = new BatchContext(UUID.randomUUID(), prompts.size());
            persistentStore.create(context.batchId(), prompts);
            store.save(context);
            BatchSnapshot accepted = context.snapshot();
            for (int index = 0; index < prompts.size(); index++) {
                int promptIndex = index;
                executor.execute(() -> processor.process(
                        context, prompts.get(promptIndex), promptIndex));
            }
            return accepted;
        }
    }

    public BatchSnapshot get(UUID batchId) {
        return persistentStore.find(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
    }
}
