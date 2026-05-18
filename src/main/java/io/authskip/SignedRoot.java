package io.authskip;

public record SignedRoot(String rootHash, String algorithm, String publicKey, String signature) {
}
