package com.batchengine.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.batchengine.config.InferenceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetryableInferenceServiceTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void retriesTwoRateLimitsWithExponentialBackoffThenSucceeds() throws Exception {
        InferenceClient client = mock(InferenceClient.class);
        Sleeper sleeper = mock(Sleeper.class);
        when(client.infer("prompt", 0, 1)).thenThrow(new RateLimitException("429"));
        when(client.infer("prompt", 0, 2)).thenThrow(new RateLimitException("429"));
        when(client.infer("prompt", 0, 3)).thenReturn("result");

        InferenceOutcome outcome = service(client, sleeper).infer("prompt", 0);

        assertThat(outcome).isEqualTo(new InferenceOutcome("result", 3));
        verify(sleeper).sleep(100);
        verify(sleeper).sleep(200);
    }

    @Test
    void exhaustionReportsThreeAttempts() {
        InferenceClient client = mock(InferenceClient.class);
        Sleeper sleeper = mock(Sleeper.class);
        when(client.infer("prompt", 0, 1)).thenThrow(new RateLimitException("429"));
        when(client.infer("prompt", 0, 2)).thenThrow(new RateLimitException("429"));
        when(client.infer("prompt", 0, 3)).thenThrow(new RateLimitException("429"));

        assertThatThrownBy(() -> service(client, sleeper).infer("prompt", 0))
                .isInstanceOfSatisfying(InferenceExecutionException.class,
                        exception -> assertThat(exception.getAttempts()).isEqualTo(3));
    }

    @Test
    void nonRateLimitFailureIsNotRetried() throws Exception {
        InferenceClient client = mock(InferenceClient.class);
        Sleeper sleeper = mock(Sleeper.class);
        when(client.infer("prompt", 0, 1)).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service(client, sleeper).infer("prompt", 0))
                .isInstanceOfSatisfying(InferenceExecutionException.class,
                        exception -> assertThat(exception.getAttempts()).isEqualTo(1));
        verify(sleeper, never()).sleep(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void interruptedBackoffRestoresInterruptFlag() throws Exception {
        InferenceClient client = mock(InferenceClient.class);
        Sleeper sleeper = mock(Sleeper.class);
        when(client.infer("prompt", 0, 1)).thenThrow(new RateLimitException("429"));
        org.mockito.Mockito.doThrow(new InterruptedException()).when(sleeper).sleep(100);

        assertThatThrownBy(() -> service(client, sleeper).infer("prompt", 0))
                .isInstanceOfSatisfying(InferenceExecutionException.class,
                        exception -> assertThat(exception.getAttempts()).isEqualTo(1));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private RetryableInferenceService service(InferenceClient client, Sleeper sleeper) {
        InferenceProperties properties = new InferenceProperties();
        properties.setMaxAttempts(3);
        properties.setInitialBackoffMs(100);
        return new RetryableInferenceService(client, sleeper, properties);
    }
}
