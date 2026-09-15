package com.batchengine.exception;

import java.util.UUID;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(UUID batchId) {
        super("Batch not found: " + batchId);
    }
}
