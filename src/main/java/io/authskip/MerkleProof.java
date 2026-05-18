package io.authskip;

import java.util.List;

public record MerkleProof(String leafValue, List<Sibling> siblings, String rootHash) {
    public String leafHash() {
        return Hashing.hex(Hashing.taggedHash("leaf", leafValue));
    }

    public boolean verify() {
        byte[] current = Hashing.taggedHash("leaf", leafValue);
        for (Sibling sibling : siblings) {
            byte[] siblingHash = java.util.HexFormat.of().parseHex(sibling.hash());
            current = sibling.position() == Position.LEFT
                    ? Hashing.parent(siblingHash, current)
                    : Hashing.parent(current, siblingHash);
        }
        return Hashing.hex(current).equals(rootHash);
    }

    public record Sibling(Position position, String hash) {
    }

    public enum Position {
        LEFT,
        RIGHT
    }
}
