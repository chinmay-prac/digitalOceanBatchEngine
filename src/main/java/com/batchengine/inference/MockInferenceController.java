package com.batchengine.inference;

import com.batchengine.config.InferenceProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/v1/inference")
public class MockInferenceController {

    private final InferenceProperties properties;

    public MockInferenceController(InferenceProperties properties) {
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<InferenceResponse> infer(@RequestBody InferenceRequest request) {
        int every = properties.getMockRateLimitEvery();
        if (every > 0
                && (request.promptIndex() + 1) % every == 0
                && request.attempt() == 1) {
            return ResponseEntity.status(429)
                    .body(new InferenceResponse(null, "Mock rate limit"));
        }
        return ResponseEntity.ok(
                new InferenceResponse("Mock inference: " + request.prompt(), null));
    }

    public record InferenceRequest(String prompt, int promptIndex, int attempt) {
    }

    public record InferenceResponse(String output, String error) {
    }
}
