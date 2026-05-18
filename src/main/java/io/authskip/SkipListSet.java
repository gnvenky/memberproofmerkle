package io.authskip;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class SkipListSet<T extends Comparable<T>> implements Iterable<T> {
    private static final int MAX_LEVEL = 16;

    private final Node<T> head = new Node<>(null, MAX_LEVEL);
    private int size;

    public boolean add(T value) {
        List<Node<T>> update = predecessors(value);
        Node<T> next = update.get(0).next[0];
        if (next != null && next.value.compareTo(value) == 0) {
            return false;
        }

        int level = deterministicLevel(value);
        Node<T> node = new Node<>(value, level);
        for (int i = 0; i <= level; i++) {
            node.next[i] = update.get(i).next[i];
            update.get(i).next[i] = node;
        }
        size++;
        return true;
    }

    public boolean contains(T value) {
        Node<T> current = head;
        for (int level = MAX_LEVEL; level >= 0; level--) {
            while (current.next[level] != null && current.next[level].value.compareTo(value) < 0) {
                current = current.next[level];
            }
        }
        current = current.next[0];
        return current != null && current.value.compareTo(value) == 0;
    }

    public List<T> toSortedList() {
        List<T> values = new ArrayList<>(size);
        for (T value : this) {
            values.add(value);
        }
        return values;
    }

    public int size() {
        return size;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> current = head.next[0];

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                T value = current.value;
                current = current.next[0];
                return value;
            }
        };
    }

    private List<Node<T>> predecessors(T value) {
        List<Node<T>> update = new ArrayList<>(MAX_LEVEL + 1);
        for (int i = 0; i <= MAX_LEVEL; i++) {
            update.add(head);
        }

        Node<T> current = head;
        for (int level = MAX_LEVEL; level >= 0; level--) {
            while (current.next[level] != null && current.next[level].value.compareTo(value) < 0) {
                current = current.next[level];
            }
            update.set(level, current);
        }
        return update;
    }

    private static int deterministicLevel(Object value) {
        byte[] hash = Hashing.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
        int level = 0;
        for (byte b : hash) {
            for (int bit = 0; bit < 8 && level < MAX_LEVEL; bit++) {
                if (((b >>> bit) & 1) == 1) {
                    return level;
                }
                level++;
            }
        }
        return level;
    }

    private static final class Node<T> {
        private final T value;
        private final Node<T>[] next;

        @SuppressWarnings("unchecked")
        private Node(T value, int level) {
            this.value = value;
            this.next = (Node<T>[]) new Node[level + 1];
        }
    }
}
