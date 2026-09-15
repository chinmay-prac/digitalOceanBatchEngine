package com.batchengine.inference;

public interface InferenceClient {
    String infer(String prompt, int promptIndex, int attempt);
}
