package com.batchengine.inference;

public class InferenceExecutionException extends RuntimeException {

    private final int attempts;

    public InferenceExecutionException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}
