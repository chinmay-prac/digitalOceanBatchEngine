package com.batchengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.batchengine.config.InferenceProperties;
import com.batchengine.exception.BatchCapacityException;
import com.batchengine.store.InMemoryBatchStore;
import com.batchengine.store.JdbcBatchStore;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BatchServiceCapacityTest {

    @Test
    void saturatedExecutorRejectsWholeNextBatch() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BatchProcessor processor = mock(BatchProcessor.class);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(processor).process(any(), anyString(), anyInt());

        Fixture fixture = fixture(1, 1, processor);
        try {
            fixture.service().submit(List.of("one", "two"));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> fixture.service().submit(List.of("three")))
                    .isInstanceOf(BatchCapacityException.class);
        } finally {
            release.countDown();
            fixture.executor().shutdown();
        }
    }

    @Test
    void runningWorkNeverExceedsConfiguredWorkerCount() throws Exception {
        int workerCount = 2;
        int taskCount = 8;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(taskCount);
        BatchProcessor processor = mock(BatchProcessor.class);
        doAnswer(invocation -> {
            int now = active.incrementAndGet();
            maximum.accumulateAndGet(now, Math::max);
            Thread.sleep(25);
            active.decrementAndGet();
            completed.countDown();
            return null;
        }).when(processor).process(any(), anyString(), anyInt());

        Fixture fixture = fixture(workerCount, taskCount, processor);
        try {
            fixture.service().submit(
                    java.util.stream.IntStream.range(0, taskCount)
                            .mapToObj(index -> "prompt-" + index)
                            .toList());
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum).hasValueLessThanOrEqualTo(workerCount);
        } finally {
            fixture.executor().shutdown();
        }
    }

    private Fixture fixture(int workers, int queueCapacity, BatchProcessor processor) {
        InferenceProperties properties = new InferenceProperties();
        properties.setWorkerCount(workers);
        properties.setQueueCapacity(queueCapacity);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        JdbcBatchStore persistentStore = mock(JdbcBatchStore.class);
        BatchService service = new BatchService(
                new InMemoryBatchStore(),
                persistentStore,
                processor,
                mock(CompletionPersistenceCoordinator.class),
                executor,
                new ExecutorCapacity(properties));
        return new Fixture(service, executor);
    }

    private record Fixture(BatchService service, ThreadPoolTaskExecutor executor) {
    }
}
