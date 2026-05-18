package io.authskip.store;

import io.authskip.Edge;
import io.authskip.MerkleProof;
import io.authskip.SignedRoot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryEdgeRepository implements EdgeRepository {
    private final List<Edge> edges = new ArrayList<>();
    private final List<RootCommit> roots = new ArrayList<>();
    private final Map<String, MerkleProof> proofs = new HashMap<>();

    @Override
    public void saveEdge(Edge edge) {
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
    }

    @Override
    public List<Edge> loadActiveEdges() {
        return List.copyOf(edges);
    }

    @Override
    public List<StoredEdge> loadActiveStoredEdges() {
        List<StoredEdge> storedEdges = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            storedEdges.add(new StoredEdge(i + 1L, edges.get(i)));
        }
        return storedEdges;
    }

    @Override
    public RootCommit saveSignedRoot(SignedRoot signedRoot) {
        RootCommit commit = new RootCommit(roots.size() + 1L, signedRoot);
        roots.add(commit);
        return commit;
    }

    @Override
    public Optional<RootCommit> latestRoot() {
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(roots.get(roots.size() - 1));
    }

    @Override
    public void saveProof(long rootVersion, long edgeId, MerkleProof proof) {
        proofs.put(rootVersion + ":" + edgeId, proof);
    }

    @Override
    public List<MerkleProof> loadProofs(long rootVersion, List<Edge> chain) {
        List<MerkleProof> loaded = new ArrayList<>();
        for (Edge edge : chain) {
            int edgeIndex = edges.indexOf(edge);
            if (edgeIndex < 0) {
                throw new IllegalArgumentException("No stored edge for proof: " + edge);
            }
            MerkleProof proof = proofs.get(rootVersion + ":" + (edgeIndex + 1L));
            if (proof == null) {
                throw new IllegalArgumentException("No stored proof for edge: " + edge);
            }
            loaded.add(proof);
        }
        return List.copyOf(loaded);
    }
}
