package com.batchengine.inference;

import com.batchengine.config.InferenceProperties;
import org.springframework.stereotype.Component;

@Component
public class MockInferenceClient implements InferenceClient {

    private final InferenceProperties properties;

    public MockInferenceClient(InferenceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String infer(String prompt, int promptIndex, int attempt) {
        int every = properties.getMockRateLimitEvery();
        if (every > 0 && (promptIndex + 1) % every == 0 && attempt == 1) {
            throw new RateLimitException("Mock HTTP 429");
        }
        return "Mock inference: " + prompt;
    }
}
