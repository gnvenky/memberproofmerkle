package io.authskip;

import io.authskip.store.PostgresEdgeRepository;
import io.authskip.store.RootCommit;
import io.authskip.store.StoredEdge;

import java.util.List;

public final class DemoPostgres {
    private DemoPostgres() {
    }

    public static void main(String[] args) {
        String jdbcUrl = env("AUTHSKIP_JDBC_URL", "jdbc:postgresql://localhost:5432/authskip");
        String username = env("AUTHSKIP_DB_USER", "authskip");
        String password = env("AUTHSKIP_DB_PASSWORD", "authskip");

        try (PostgresEdgeRepository repository = new PostgresEdgeRepository(jdbcUrl, username, password)) {
            seed(repository);

            List<StoredEdge> persistedEdges = repository.loadActiveStoredEdges();
            MembershipGraph graph = MembershipGraph.fromEdges(persistedEdges.stream()
                    .map(StoredEdge::edge)
                    .toList());
            ChainProof proof = graph.check("user:alice", "member", "org:acme");

            RootSigner signer = RootSigner.create();
            SignedRoot signedRoot = signer.sign(proof.rootHash());
            RootCommit commit = repository.saveSignedRoot(signedRoot);

            for (StoredEdge storedEdge : persistedEdges) {
                repository.saveProof(commit.version(), storedEdge.edgeId(), graph.prove(storedEdge.edge()));
            }

            List<MerkleProof> persistedProofs = repository.loadProofs(commit.version(), proof.chain());
            ChainProof persistedChainProof = new ChainProof(
                    proof.allowed(),
                    proof.chain(),
                    persistedProofs,
                    proof.rootHash());

            System.out.println("loaded_edges: " + persistedEdges.size());
            System.out.println("root_version: " + commit.version());
            System.out.println("allowed: " + proof.allowed());
            System.out.println("root_hash: " + proof.rootHash());
            System.out.println("root_signature_persisted: " + repository.latestRoot().isPresent());
            System.out.println("root_signature_valid: " + RootSigner.verify(signedRoot, signer.publicKey()));
            System.out.println("persisted_chain_proofs: " + persistedProofs.size());
            System.out.println("chain:");
            for (Edge edge : proof.chain()) {
                System.out.println("  - " + edge);
            }
            System.out.println("generated proof verifies: " + proof.verify());
            System.out.println("persisted proof verifies: " + persistedChainProof.verify());
        }
    }

    private static void seed(PostgresEdgeRepository repository) {
        repository.saveEdge(new Edge("user:alice", "member", "team:platform"));
        repository.saveEdge(new Edge("team:platform", "member", "org:acme"));
        repository.saveEdge(new Edge("user:bob", "member", "team:data"));
        repository.saveEdge(new Edge("team:data", "member", "org:acme"));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
