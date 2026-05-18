package io.authskip;

public record Edge(String subject, String relation, String object) implements Comparable<Edge> {
    public Edge {
        if (isBlank(subject) || isBlank(relation) || isBlank(object)) {
            throw new IllegalArgumentException("subject, relation, and object are required");
        }
    }

    public String canonical() {
        return subject + "\u001f" + relation + "\u001f" + object;
    }

    @Override
    public int compareTo(Edge other) {
        return canonical().compareTo(other.canonical());
    }

    @Override
    public String toString() {
        return subject + " " + relation + " " + object;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
