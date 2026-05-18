package io.authskip;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class MembershipGraph {
    private final AuthenticatedEdgeSet authenticatedEdges = new AuthenticatedEdgeSet();
    private final Map<String, List<Edge>> outgoing = new HashMap<>();

    public static MembershipGraph fromEdges(List<Edge> edges) {
        MembershipGraph graph = new MembershipGraph();
        for (Edge edge : edges) {
            graph.addEdge(edge);
        }
        return graph;
    }

    public void addEdge(String subject, String relation, String object) {
        addEdge(new Edge(subject, relation, object));
    }

    public void addEdge(Edge edge) {
        if (authenticatedEdges.add(edge)) {
            outgoing.computeIfAbsent(edge.subject(), ignored -> new ArrayList<>()).add(edge);
        }
    }

    public ChainProof check(String subject, String relation, String object) {
        Optional<List<Edge>> chain = findChain(subject, relation, object);
        if (chain.isEmpty()) {
            return new ChainProof(false, List.of(), List.of(), authenticatedEdges.rootHash());
        }

        List<MerkleProof> proofs = chain.get().stream()
                .map(authenticatedEdges::prove)
                .toList();
        return new ChainProof(true, List.copyOf(chain.get()), proofs, authenticatedEdges.rootHash());
    }

    public String rootHash() {
        return authenticatedEdges.rootHash();
    }

    public MerkleProof prove(Edge edge) {
        return authenticatedEdges.prove(edge);
    }

    private Optional<List<Edge>> findChain(String subject, String relation, String object) {
        Queue<PathState> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new PathState(subject, List.of()));
        visited.add(subject);

        while (!queue.isEmpty()) {
            PathState state = queue.remove();
            for (Edge edge : outgoing.getOrDefault(state.node, List.of())) {
                if (!edge.relation().equals(relation)) {
                    continue;
                }

                List<Edge> nextPath = new ArrayList<>(state.path);
                nextPath.add(edge);
                if (edge.object().equals(object)) {
                    return Optional.of(nextPath);
                }
                if (visited.add(edge.object())) {
                    queue.add(new PathState(edge.object(), nextPath));
                }
            }
        }

        return Optional.empty();
    }

    private record PathState(String node, List<Edge> path) {
    }
}
