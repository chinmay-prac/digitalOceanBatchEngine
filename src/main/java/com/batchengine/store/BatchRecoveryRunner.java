package com.batchengine.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRecoveryRunner.class);
    private final JdbcBatchStore store;

    public BatchRecoveryRunner(JdbcBatchStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recovered = store.recoverIncomplete();
        if (recovered > 0) {
            log.warn("Recovered {} incomplete persisted batches as failed", recovered);
        }
    }
}
