package io.authskip;

import java.util.ArrayList;
import java.util.List;

final class MerkleTree {
    private final List<String> leaves;
    private final List<List<byte[]>> levels;

    MerkleTree(List<String> sortedLeaves) {
        if (sortedLeaves.isEmpty()) {
            throw new IllegalArgumentException("cannot build a Merkle tree with no leaves");
        }
        this.leaves = List.copyOf(sortedLeaves);
        this.levels = buildLevels(sortedLeaves);
    }

    String rootHash() {
        return Hashing.hex(levels.get(levels.size() - 1).get(0));
    }

    MerkleProof prove(String leafValue) {
        int index = leaves.indexOf(leafValue);
        if (index < 0) {
            throw new IllegalArgumentException("leaf is not present: " + leafValue);
        }

        List<MerkleProof.Sibling> siblings = new ArrayList<>();
        int cursor = index;
        for (int level = 0; level < levels.size() - 1; level++) {
            List<byte[]> nodes = levels.get(level);
            boolean isRight = cursor % 2 == 1;
            int siblingIndex = isRight ? cursor - 1 : cursor + 1;
            if (siblingIndex >= nodes.size()) {
                siblingIndex = cursor;
            }

            siblings.add(new MerkleProof.Sibling(
                    isRight ? MerkleProof.Position.LEFT : MerkleProof.Position.RIGHT,
                    Hashing.hex(nodes.get(siblingIndex))));
            cursor = cursor / 2;
        }

        return new MerkleProof(leafValue, List.copyOf(siblings), rootHash());
    }

    private static List<List<byte[]>> buildLevels(List<String> leaves) {
        List<List<byte[]>> levels = new ArrayList<>();
        List<byte[]> current = leaves.stream()
                .map(leaf -> Hashing.taggedHash("leaf", leaf))
                .toList();
        levels.add(current);

        while (current.size() > 1) {
            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < current.size(); i += 2) {
                byte[] left = current.get(i);
                byte[] right = i + 1 < current.size() ? current.get(i + 1) : left;
                next.add(Hashing.parent(left, right));
            }
            current = next;
            levels.add(current);
        }

        return levels;
    }
}
