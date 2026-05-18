package io.authskip.store;

import io.authskip.Edge;
import io.authskip.MerkleProof;
import io.authskip.SignedRoot;

import java.util.List;
import java.util.Optional;

public interface EdgeRepository {
    void saveEdge(Edge edge);

    List<Edge> loadActiveEdges();

    List<StoredEdge> loadActiveStoredEdges();

    RootCommit saveSignedRoot(SignedRoot signedRoot);

    Optional<RootCommit> latestRoot();

    void saveProof(long rootVersion, long edgeId, MerkleProof proof);

    List<MerkleProof> loadProofs(long rootVersion, List<Edge> chain);
}
