package com.batchengine.store;

import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.BatchStatus;
import com.batchengine.model.PromptResult;
import com.batchengine.model.PromptStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBatchStore {

    private final JdbcTemplate jdbc;

    public JdbcBatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(UUID batchId, List<String> prompts) {
        jdbc.update("""
                INSERT INTO batches
                    (batch_id, status, total, completed, failed, finished)
                VALUES (?, 'ACCEPTED', ?, 0, 0, 0)
                """, batchId, prompts.size());

        jdbc.batchUpdate("""
                INSERT INTO batch_prompts
                    (batch_id, prompt_index, prompt_text, status, attempts)
                VALUES (?, ?, ?, 'PENDING', 0)
                """, IntStream.range(0, prompts.size())
                .mapToObj(index -> new Object[] {batchId, index, prompts.get(index)})
                .toList());
    }

    @Transactional
    public void markProcessing(UUID batchId, int promptIndex) {
        jdbc.update("""
                UPDATE batch_prompts
                SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
                WHERE batch_id = ? AND prompt_index = ? AND status = 'PENDING'
                """, batchId, promptIndex);
        jdbc.update("""
                UPDATE batches
                SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
                WHERE batch_id = ? AND status = 'ACCEPTED'
                """, batchId);
    }

    @Transactional
    public boolean complete(UUID batchId, PromptResult result) {
        lockBatch(batchId);
        int changed = jdbc.update("""
                UPDATE batch_prompts
                SET status = ?, output = ?, attempts = ?, error = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE batch_id = ? AND prompt_index = ?
                  AND status IN ('PENDING', 'PROCESSING')
                """, result.status().name(), result.output(), result.attempts(), result.error(),
                batchId, result.index());
        if (changed == 0) {
            return false;
        }
        recomputeBatch(batchId);
        return true;
    }

    public Optional<BatchSnapshot> find(UUID batchId) {
        try {
            BatchRow batch = jdbc.queryForObject("""
                    SELECT batch_id, status, total, completed, failed, finished
                    FROM batches WHERE batch_id = ?
                    """, this::mapBatch, batchId);
            List<PromptResult> results = jdbc.query("""
                    SELECT prompt_index, status, output, attempts, error
                    FROM batch_prompts
                    WHERE batch_id = ? AND status IN ('COMPLETED', 'FAILED')
                    ORDER BY prompt_index
                    """, this::mapPromptResult, batchId);
            return Optional.of(new BatchSnapshot(
                    batch.batchId(), batch.status(), batch.total(), batch.completed(),
                    batch.failed(), batch.finished(), List.copyOf(results)));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public int recoverIncomplete() {
        List<UUID> batchIds = jdbc.queryForList("""
                SELECT batch_id FROM batches
                WHERE status IN ('ACCEPTED', 'PROCESSING')
                """, UUID.class);
        for (UUID batchId : batchIds) {
            lockBatch(batchId);
            jdbc.update("""
                    UPDATE batch_prompts
                    SET status = 'FAILED',
                        error = 'Application restarted before completion',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE batch_id = ? AND status IN ('PENDING', 'PROCESSING')
                    """, batchId);
            recomputeBatch(batchId);
        }
        return batchIds.size();
    }

    private void lockBatch(UUID batchId) {
        jdbc.queryForObject(
                "SELECT batch_id FROM batches WHERE batch_id = ? FOR UPDATE",
                UUID.class, batchId);
    }

    private void recomputeBatch(UUID batchId) {
        Counts counts = jdbc.queryForObject("""
                SELECT b.total,
                       SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN p.status = 'FAILED' THEN 1 ELSE 0 END)
                FROM batches b
                JOIN batch_prompts p ON p.batch_id = b.batch_id
                WHERE b.batch_id = ?
                GROUP BY b.total
                """, (resultSet, rowNumber) -> new Counts(
                        resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3)), batchId);
        int finished = counts.completed() + counts.failed();
        BatchStatus status = finished < counts.total()
                ? BatchStatus.PROCESSING
                : counts.completed() == counts.total()
                        ? BatchStatus.COMPLETED
                        : counts.completed() == 0
                                ? BatchStatus.FAILED
                                : BatchStatus.PARTIALLY_FAILED;
        jdbc.update("""
                UPDATE batches
                SET status = ?, completed = ?, failed = ?, finished = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE batch_id = ?
                """, status.name(), counts.completed(), counts.failed(), finished, batchId);
    }

    private BatchRow mapBatch(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BatchRow(
                resultSet.getObject("batch_id", UUID.class),
                BatchStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("total"),
                resultSet.getInt("completed"),
                resultSet.getInt("failed"),
                resultSet.getInt("finished"));
    }

    private PromptResult mapPromptResult(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptResult(
                resultSet.getInt("prompt_index"),
                PromptStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("output"),
                resultSet.getInt("attempts"),
                resultSet.getString("error"));
    }

    private record BatchRow(
            UUID batchId,
            BatchStatus status,
            int total,
            int completed,
            int failed,
            int finished) {
    }

    private record Counts(int total, int completed, int failed) {
    }
}
