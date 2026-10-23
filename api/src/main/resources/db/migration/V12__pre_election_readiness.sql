ALTER TABLE pollster_performance ADD COLUMN verified_at TIMESTAMPTZ, ADD COLUMN notes VARCHAR(5000);
-- Legacy provenance is intentionally left untouched and flagged by data quality.
CREATE TABLE pre_election_freezes (
 id UUID PRIMARY KEY,label VARCHAR(200) NOT NULL CHECK(btrim(label)<>''),frozen_at TIMESTAMPTZ NOT NULL,
 git_commit VARCHAR(100),aggregation_configuration JSONB NOT NULL,poll_ids JSONB NOT NULL,
 pollster_performance_ids JSONB NOT NULL,aggregate_results JSONB NOT NULL,composition JSONB NOT NULL,
 notes VARCHAR(5000),active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX one_active_pre_election_freeze ON pre_election_freezes(active) WHERE active;
CREATE FUNCTION reject_pre_election_freeze_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'PreElectionFreeze is immutable'; END $$;
CREATE TRIGGER immutable_pre_election_freeze BEFORE UPDATE OR DELETE ON pre_election_freezes
 FOR EACH ROW EXECUTE FUNCTION reject_pre_election_freeze_mutation();
