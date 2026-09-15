package com.batchengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.batchengine.exception.BatchCapacityException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void executorSaturationMapsToServiceUnavailable() {
        var response = new GlobalExceptionHandler()
                .unavailable(new BatchCapacityException());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("EXECUTOR_CAPACITY_EXCEEDED");
    }
}
