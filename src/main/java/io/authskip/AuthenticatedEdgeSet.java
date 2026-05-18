package io.authskip;

import java.util.List;

public final class AuthenticatedEdgeSet {
    private final SkipListSet<Edge> edges = new SkipListSet<>();

    public boolean add(Edge edge) {
        return edges.add(edge);
    }

    public boolean contains(Edge edge) {
        return edges.contains(edge);
    }

    public List<Edge> sortedEdges() {
        return edges.toSortedList();
    }

    public String rootHash() {
        if (edges.size() == 0) {
            return Hashing.hex(Hashing.taggedHash("root", "empty"));
        }
        return tree().rootHash();
    }

    public MerkleProof prove(Edge edge) {
        if (!contains(edge)) {
            throw new IllegalArgumentException("edge is not present: " + edge);
        }
        return tree().prove(edge.canonical());
    }

    private MerkleTree tree() {
        List<String> leaves = edges.toSortedList().stream()
                .map(Edge::canonical)
                .toList();
        return new MerkleTree(leaves);
    }
}
