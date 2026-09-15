package com.batchengine.inference;

import com.batchengine.config.InferenceProperties;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class MockInferenceClient implements InferenceClient {

    private final RestClient.Builder restClientBuilder;
    private final ApplicationContext applicationContext;
    private final InferenceProperties properties;

    public MockInferenceClient(
            RestClient.Builder restClientBuilder,
            ApplicationContext applicationContext,
            InferenceProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public String infer(String prompt, int promptIndex, int attempt) {
        RestClient client = restClientBuilder
                .baseUrl(endpointBaseUrl())
                .build();
        try {
            MockInferenceController.InferenceResponse response = client.post()
                    .uri("/mock/v1/inference")
                    .body(new MockInferenceController.InferenceRequest(
                            prompt, promptIndex, attempt))
                    .retrieve()
                    .body(MockInferenceController.InferenceResponse.class);
            if (response == null || response.output() == null) {
                throw new IllegalStateException("Mock inference returned no output");
            }
            return response.output();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RateLimitException("Mock HTTP 429");
            }
            throw exception;
        }
    }

    private String endpointBaseUrl() {
        if (!properties.getEndpointBaseUrl().isBlank()) {
            return properties.getEndpointBaseUrl();
        }
        if (applicationContext instanceof ServletWebServerApplicationContext webContext) {
            return "http://127.0.0.1:" + webContext.getWebServer().getPort();
        }
        throw new IllegalStateException("Inference endpoint URL requires a running web server");
    }
}
