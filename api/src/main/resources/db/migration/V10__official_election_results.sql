CREATE TABLE election_result_snapshots (
 id UUID PRIMARY KEY, election_id VARCHAR(6) NOT NULL, election_round INTEGER NOT NULL,
 scope_type VARCHAR(20) NOT NULL, scope_code VARCHAR(32) NOT NULL,
 source_generated_at TIMESTAMPTZ NOT NULL, captured_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(20) NOT NULL, sections_percentage NUMERIC(12,9),
 total_sections BIGINT, totalized_sections BIGINT, electorate BIGINT,
 total_votes BIGINT, valid_votes BIGINT, blank_votes BIGINT, null_votes BIGINT,
 candidate_results JSONB NOT NULL, payload JSONB NOT NULL, source_etag TEXT,
 content_hash VARCHAR(64) NOT NULL,
 CONSTRAINT uq_official_generation UNIQUE(election_id,election_round,scope_type,scope_code,source_generated_at,content_hash)
);
CREATE INDEX idx_official_history ON election_result_snapshots(election_id,election_round,scope_type,scope_code,source_generated_at DESC,captured_at DESC);
CREATE TABLE election_municipalities (
 election_id VARCHAR(6) NOT NULL, uf VARCHAR(2) NOT NULL, code VARCHAR(5) NOT NULL,
 name VARCHAR(200) NOT NULL, zones JSONB NOT NULL,
 PRIMARY KEY(election_id,uf,code), CHECK(code ~ '^[0-9]{5}$')
);
CREATE INDEX idx_official_municipality_name ON election_municipalities(election_id,uf,name);
