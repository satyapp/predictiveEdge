-- Bootstrap only. The developer-ready database design will replace this marker
-- with approved identity tables and constraints before feature implementation.
CREATE TABLE IF NOT EXISTS pe_schema_version_marker (
    marker_id INTEGER PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
