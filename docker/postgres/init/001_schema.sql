CREATE TABLE IF NOT EXISTS membership_edges (
    edge_id BIGSERIAL PRIMARY KEY,
    subject TEXT NOT NULL,
    relation TEXT NOT NULL,
    object TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject, relation, object)
);

CREATE INDEX IF NOT EXISTS idx_membership_edges_subject_relation
    ON membership_edges (subject, relation)
    WHERE active;

CREATE INDEX IF NOT EXISTS idx_membership_edges_object_relation
    ON membership_edges (object, relation)
    WHERE active;

CREATE TABLE IF NOT EXISTS committed_roots (
    root_version BIGSERIAL PRIMARY KEY,
    root_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS root_signatures (
    root_version BIGINT PRIMARY KEY REFERENCES committed_roots(root_version) ON DELETE CASCADE,
    algorithm TEXT NOT NULL,
    public_key TEXT NOT NULL,
    signature TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS edge_proofs (
    root_version BIGINT NOT NULL REFERENCES committed_roots(root_version) ON DELETE CASCADE,
    edge_id BIGINT NOT NULL REFERENCES membership_edges(edge_id) ON DELETE CASCADE,
    leaf_value TEXT NOT NULL,
    leaf_hash TEXT NOT NULL,
    proof_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (root_version, edge_id)
);
