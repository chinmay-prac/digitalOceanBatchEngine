package com.batchengine.service;

import com.batchengine.exception.BatchCapacityException;
import com.batchengine.exception.BatchNotFoundException;
import com.batchengine.model.BatchContext;
import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import com.batchengine.store.InMemoryBatchStore;
import com.batchengine.store.JdbcBatchStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class BatchService {

    private final InMemoryBatchStore store;
    private final JdbcBatchStore persistentStore;
    private final BatchProcessor processor;
    private final CompletionPersistenceCoordinator persistenceCoordinator;
    private final ThreadPoolTaskExecutor executor;
    private final ExecutorCapacity capacity;

    public BatchService(
            InMemoryBatchStore store,
            JdbcBatchStore persistentStore,
            BatchProcessor processor,
            CompletionPersistenceCoordinator persistenceCoordinator,
            ThreadPoolTaskExecutor inferenceExecutor,
            ExecutorCapacity capacity) {
        this.store = store;
        this.persistentStore = persistentStore;
        this.processor = processor;
        this.persistenceCoordinator = persistenceCoordinator;
        this.executor = inferenceExecutor;
        this.capacity = capacity;
    }

    public BatchSnapshot submit(List<String> prompts) {
        if (!capacity.tryReserve(prompts.size())) {
            throw new BatchCapacityException();
        }

        BatchContext context = new BatchContext(UUID.randomUUID(), prompts.size());
        try {
            persistentStore.create(context.batchId(), prompts);
        } catch (RuntimeException exception) {
            capacity.release(prompts.size());
            throw exception;
        }
        store.save(context);
        BatchSnapshot accepted = context.snapshot();
        int scheduled = 0;
        try {
            for (; scheduled < prompts.size(); scheduled++) {
                int promptIndex = scheduled;
                executor.execute(() -> {
                    try {
                        processor.process(context, prompts.get(promptIndex), promptIndex);
                    } finally {
                        capacity.release();
                    }
                });
            }
        } catch (RejectedExecutionException exception) {
            failUnscheduled(context, scheduled, prompts.size());
        }
        return accepted;
    }

    private void failUnscheduled(BatchContext context, int firstIndex, int total) {
        capacity.release(total - firstIndex);
        for (int index = firstIndex; index < total; index++) {
            context.markProcessing(index);
            PromptResult result = new PromptResult(
                    index, PromptStatus.FAILED, null, 0,
                    "Executor shut down before prompt could be scheduled");
            persistenceCoordinator.persist(context.batchId(), result);
            context.complete(result);
        }
    }

    public BatchSnapshot get(UUID batchId) {
        return persistentStore.find(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));
    }
}
