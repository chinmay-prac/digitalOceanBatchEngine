package com.batchengine.exception;

public class BatchCapacityException extends RuntimeException {
    public BatchCapacityException() {
        super("The inference executor does not have capacity for the complete batch");
    }
}
