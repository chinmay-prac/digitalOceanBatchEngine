package com.batchengine.exception;

public class InvalidBatchFileException extends RuntimeException {
    public InvalidBatchFileException(String message) {
        super(message);
    }
}
