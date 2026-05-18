package io.authskip;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Hashing {
    private Hashing() {
    }

    static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static byte[] taggedHash(String tag, String value) {
        return sha256((tag + ":" + value).getBytes(StandardCharsets.UTF_8));
    }

    static byte[] parent(byte[] left, byte[] right) {
        byte[] input = new byte[1 + left.length + right.length];
        input[0] = 1;
        System.arraycopy(left, 0, input, 1, left.length);
        System.arraycopy(right, 0, input, 1 + left.length, right.length);
        return sha256(input);
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
