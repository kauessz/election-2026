-- Existing rows do not prove when (or whether) they were exposed live. Never backfill publication time.
ALTER TABLE projection_snapshots ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
ALTER TABLE projection_snapshots ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE projection_snapshots ADD COLUMN input_manifest JSONB;
ALTER TABLE projection_snapshots ADD CONSTRAINT projection_origin_check CHECK(origin IN ('LIVE','REPLAY','LEGACY_UNKNOWN'));
ALTER TABLE projection_snapshots ADD CONSTRAINT projection_publication_check CHECK(
 (origin='LIVE' AND published_at IS NOT NULL AND input_manifest IS NOT NULL) OR
 (origin<>'LIVE' AND published_at IS NULL));
CREATE INDEX idx_projection_publication_history ON projection_snapshots(origin,calculated_at,id);
