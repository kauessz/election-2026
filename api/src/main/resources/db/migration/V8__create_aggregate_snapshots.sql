CREATE TABLE aggregate_snapshots (
    id UUID PRIMARY KEY,
    method VARCHAR(20) NOT NULL CHECK (method IN ('SIMPLE','RECENCY','WEIGHTED','CALIBRATED')),
    calculated_at TIMESTAMPTZ NOT NULL,
    snapshot_minute TIMESTAMPTZ NOT NULL CHECK (snapshot_minute = date_trunc('minute', snapshot_minute)),
    reference_date DATE NOT NULL,
    poll_count INTEGER NOT NULL CHECK (poll_count > 0),
    pollster_count INTEGER NOT NULL CHECK (pollster_count = poll_count),
    total_sample_size BIGINT NOT NULL CHECK (total_sample_size >= 0),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT aggregate_snapshot_period_unique UNIQUE(method, reference_date, snapshot_minute)
);
CREATE INDEX aggregate_snapshot_history_idx ON aggregate_snapshots(calculated_at, id);
CREATE INDEX aggregate_snapshot_method_history_idx ON aggregate_snapshots(method, calculated_at, id);
