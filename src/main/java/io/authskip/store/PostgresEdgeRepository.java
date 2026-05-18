package io.authskip.store;

import io.authskip.Edge;
import io.authskip.MerkleProof;
import io.authskip.SignedRoot;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresEdgeRepository implements EdgeRepository, AutoCloseable {
    private final Connection connection;

    public PostgresEdgeRepository(String jdbcUrl, String username, String password) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl, username, password);
            ensureSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to connect to Postgres", e);
        }
    }

    @Override
    public void saveEdge(Edge edge) {
        String sql = """
                INSERT INTO membership_edges (subject, relation, object)
                VALUES (?, ?, ?)
                ON CONFLICT (subject, relation, object)
                DO UPDATE SET active = TRUE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, edge.subject());
            statement.setString(2, edge.relation());
            statement.setString(3, edge.object());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save edge: " + edge, e);
        }
    }

    @Override
    public List<Edge> loadActiveEdges() {
        return loadActiveStoredEdges().stream()
                .map(StoredEdge::edge)
                .toList();
    }

    @Override
    public List<StoredEdge> loadActiveStoredEdges() {
        String sql = """
                SELECT edge_id, subject, relation, object
                FROM membership_edges
                WHERE active = TRUE
                ORDER BY subject, relation, object
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<StoredEdge> edges = new ArrayList<>();
            while (resultSet.next()) {
                edges.add(new StoredEdge(
                        resultSet.getLong("edge_id"),
                        new Edge(
                                resultSet.getString("subject"),
                                resultSet.getString("relation"),
                                resultSet.getString("object"))));
            }
            return edges;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load active edges", e);
        }
    }

    @Override
    public RootCommit saveSignedRoot(SignedRoot signedRoot) {
        try {
            connection.setAutoCommit(false);
            long version = upsertRoot(signedRoot.rootHash());
            upsertSignature(version, signedRoot);
            connection.commit();
            return new RootCommit(version, signedRoot);
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Unable to save signed root", e);
        } finally {
            resetAutoCommit();
        }
    }

    @Override
    public Optional<RootCommit> latestRoot() {
        String sql = """
                SELECT r.root_version, r.root_hash, s.algorithm, s.public_key, s.signature
                FROM committed_roots r
                JOIN root_signatures s ON s.root_version = r.root_version
                ORDER BY r.root_version DESC
                LIMIT 1
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            SignedRoot signedRoot = new SignedRoot(
                    resultSet.getString("root_hash"),
                    resultSet.getString("algorithm"),
                    resultSet.getString("public_key"),
                    resultSet.getString("signature"));
            return Optional.of(new RootCommit(resultSet.getLong("root_version"), signedRoot));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load latest root", e);
        }
    }

    @Override
    public void saveProof(long rootVersion, long edgeId, MerkleProof proof) {
        String sql = """
                INSERT INTO edge_proofs (root_version, edge_id, leaf_value, leaf_hash, proof_json)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (root_version, edge_id)
                DO UPDATE SET
                    leaf_value = EXCLUDED.leaf_value,
                    leaf_hash = EXCLUDED.leaf_hash,
                    proof_json = EXCLUDED.proof_json
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rootVersion);
            statement.setLong(2, edgeId);
            statement.setString(3, proof.leafValue());
            statement.setString(4, MerkleProofJson.leafHash(proof));

            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(MerkleProofJson.toJson(proof));
            statement.setObject(5, json);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to save proof for edge_id " + edgeId, e);
        }
    }

    @Override
    public List<MerkleProof> loadProofs(long rootVersion, List<Edge> chain) {
        List<MerkleProof> proofs = new ArrayList<>();
        for (Edge edge : chain) {
            proofs.add(loadProof(rootVersion, edge));
        }
        return List.copyOf(proofs);
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to close Postgres connection", e);
        }
    }

    private void ensureSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS membership_edges (
                        edge_id BIGSERIAL PRIMARY KEY,
                        subject TEXT NOT NULL,
                        relation TEXT NOT NULL,
                        object TEXT NOT NULL,
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        UNIQUE (subject, relation, object)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_membership_edges_subject_relation
                    ON membership_edges (subject, relation)
                    WHERE active
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_membership_edges_object_relation
                    ON membership_edges (object, relation)
                    WHERE active
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS committed_roots (
                        root_version BIGSERIAL PRIMARY KEY,
                        root_hash TEXT NOT NULL UNIQUE,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS root_signatures (
                        root_version BIGINT PRIMARY KEY REFERENCES committed_roots(root_version) ON DELETE CASCADE,
                        algorithm TEXT NOT NULL,
                        public_key TEXT NOT NULL,
                        signature TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS edge_proofs (
                        root_version BIGINT NOT NULL REFERENCES committed_roots(root_version) ON DELETE CASCADE,
                        edge_id BIGINT NOT NULL REFERENCES membership_edges(edge_id) ON DELETE CASCADE,
                        leaf_value TEXT NOT NULL,
                        leaf_hash TEXT NOT NULL,
                        proof_json JSONB NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (root_version, edge_id)
                    )
                    """);
        }
    }

    private long upsertRoot(String rootHash) throws SQLException {
        String sql = """
                INSERT INTO committed_roots (root_hash)
                VALUES (?)
                ON CONFLICT (root_hash)
                DO UPDATE SET root_hash = EXCLUDED.root_hash
                RETURNING root_version
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rootHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("root_version");
            }
        }
    }

    private void upsertSignature(long version, SignedRoot signedRoot) throws SQLException {
        String sql = """
                INSERT INTO root_signatures (root_version, algorithm, public_key, signature)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (root_version)
                DO UPDATE SET
                    algorithm = EXCLUDED.algorithm,
                    public_key = EXCLUDED.public_key,
                    signature = EXCLUDED.signature
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, version);
            statement.setString(2, signedRoot.algorithm());
            statement.setString(3, signedRoot.publicKey());
            statement.setString(4, signedRoot.signature());
            statement.executeUpdate();
        }
    }

    private MerkleProof loadProof(long rootVersion, Edge edge) {
        String sql = """
                SELECT p.proof_json::text AS proof_json
                FROM edge_proofs p
                JOIN membership_edges e ON e.edge_id = p.edge_id
                WHERE p.root_version = ?
                  AND e.subject = ?
                  AND e.relation = ?
                  AND e.object = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rootVersion);
            statement.setString(2, edge.subject());
            statement.setString(3, edge.relation());
            statement.setString(4, edge.object());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("No persisted proof for edge at root_version "
                            + rootVersion + ": " + edge);
                }
                return MerkleProofJson.fromJson(resultSet.getString("proof_json"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load proof for edge: " + edge, e);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void resetAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }
}
