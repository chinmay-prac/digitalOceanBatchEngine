package com.batchengine.store;

import com.batchengine.model.BatchContext;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryBatchStore {

    private final ConcurrentHashMap<UUID, BatchContext> batches = new ConcurrentHashMap<>();

    public void save(BatchContext context) {
        batches.put(context.batchId(), context);
    }

    public Optional<BatchContext> find(UUID batchId) {
        return Optional.ofNullable(batches.get(batchId));
    }

    public void remove(UUID batchId) {
        batches.remove(batchId);
    }
}
