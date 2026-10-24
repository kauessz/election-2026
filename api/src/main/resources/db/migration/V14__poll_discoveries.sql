-- Inbox only: discovery never inserts operational polls or electoral results.
CREATE TABLE poll_discoveries (
 id UUID PRIMARY KEY, registration_number VARCHAR(100) NOT NULL UNIQUE,
 pollster_name VARCHAR(500) NOT NULL, pollster_slug VARCHAR(200),
 field_start DATE, field_end DATE, expected_publication_date DATE,
 sample_size INTEGER CHECK(sample_size>0), margin_of_error NUMERIC(7,4) CHECK(margin_of_error>0 AND margin_of_error<=100), methodology TEXT,
 tse_metadata JSONB NOT NULL, source_url VARCHAR(2000), vote_basis VARCHAR(10) CHECK(vote_basis IN ('TOTAL','VALID')),
 candidate_results JSONB, status VARCHAR(30) NOT NULL CHECK(status IN
 ('DETECTED','AWAITING_RESULTS','READY_FOR_REVIEW','UNMAPPED_POLLSTER','INVALID','IMPORTED','IGNORED')),
 detected_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, reviewed_at TIMESTAMPTZ,
 imported_poll_id UUID, notes VARCHAR(5000),
 payload JSONB NOT NULL, CHECK(field_end>=field_start)
);
CREATE INDEX poll_discoveries_status_date ON poll_discoveries(status,expected_publication_date);
CREATE TABLE poll_discovery_events (
 id UUID PRIMARY KEY, discovery_id UUID NOT NULL REFERENCES poll_discoveries(id),
 event_type VARCHAR(40) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, payload JSONB NOT NULL
);
CREATE INDEX poll_discovery_events_discovery ON poll_discovery_events(discovery_id,occurred_at);
