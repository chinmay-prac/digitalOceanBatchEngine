CREATE TABLE batches (
    batch_id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    total INTEGER NOT NULL CHECK (total > 0),
    completed INTEGER NOT NULL DEFAULT 0 CHECK (completed >= 0),
    failed INTEGER NOT NULL DEFAULT 0 CHECK (failed >= 0),
    finished INTEGER NOT NULL DEFAULT 0 CHECK (finished >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (finished = completed + failed),
    CHECK (finished <= total)
);

CREATE TABLE batch_prompts (
    batch_id UUID NOT NULL,
    prompt_index INTEGER NOT NULL CHECK (prompt_index >= 0),
    prompt_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    output TEXT,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    error TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id, prompt_index),
    CONSTRAINT fk_batch_prompts_batch
        FOREIGN KEY (batch_id) REFERENCES batches(batch_id) ON DELETE CASCADE
);

CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batch_prompts_status ON batch_prompts(batch_id, status);
