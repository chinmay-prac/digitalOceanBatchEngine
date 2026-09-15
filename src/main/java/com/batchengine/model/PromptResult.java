package com.batchengine.model;

public record PromptResult(
        int index,
        PromptStatus status,
        String output,
        int attempts,
        String error) {
}
