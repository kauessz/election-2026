ALTER TABLE pollster_performance ADD COLUMN source_url VARCHAR(2000);
ALTER TABLE pollster_performance ADD COLUMN source_description VARCHAR(2000);
CREATE INDEX pollster_performance_calibration_idx ON pollster_performance(election_key, round, pollster_id);

-- No performance seed: the repository contains no sourced real first-round errors.
-- Register verified data through /api/admin/pollster-performances, recording its source.
