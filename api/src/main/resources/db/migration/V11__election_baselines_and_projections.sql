CREATE TABLE election_baselines (
 id UUID PRIMARY KEY, election_id VARCHAR(6) NOT NULL, election_round INTEGER NOT NULL,
 scope_type VARCHAR(20) NOT NULL, scope_code VARCHAR(32) NOT NULL,
 electorate BIGINT NOT NULL CHECK(electorate>0),total_votes BIGINT NOT NULL,valid_votes BIGINT NOT NULL CHECK(valid_votes>0),blank_votes BIGINT,null_votes BIGINT,
 valid_votes_per_elector NUMERIC(38,24) NOT NULL,candidate_results JSONB NOT NULL,payload JSONB NOT NULL,
 source_generated_at TIMESTAMPTZ NOT NULL,captured_at TIMESTAMPTZ NOT NULL,source_url TEXT NOT NULL,
 UNIQUE(election_id,election_round,scope_type,scope_code)
);
CREATE TABLE projection_snapshots (
 id UUID PRIMARY KEY,model_version VARCHAR(50) NOT NULL,calculated_at TIMESTAMPTZ NOT NULL,
 official_generated_at TIMESTAMPTZ NOT NULL,national_electorate_progress NUMERIC(38,24) NOT NULL,
 official_generation_key VARCHAR(64) NOT NULL,projection JSONB NOT NULL,
 UNIQUE(model_version,official_generation_key)
);
CREATE INDEX idx_projection_history ON projection_snapshots(model_version,calculated_at,id);
