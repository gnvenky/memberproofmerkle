package io.authskip;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

public final class RootSigner {
    private static final String ALGORITHM = "Ed25519";

    private final KeyPair keyPair;

    private RootSigner(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public static RootSigner create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            return new RootSigner(generator.generateKeyPair());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to create Ed25519 key pair", e);
        }
    }

    public SignedRoot sign(String rootHash) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(keyPair.getPrivate());
            signer.update(rootHash.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();
            return new SignedRoot(
                    rootHash,
                    ALGORITHM,
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(signature));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign root hash", e);
        }
    }

    public static boolean verify(SignedRoot signedRoot, PublicKey publicKey) {
        try {
            Signature verifier = Signature.getInstance(signedRoot.algorithm());
            verifier.initVerify(publicKey);
            verifier.update(signedRoot.rootHash().getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signedRoot.signature()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to verify signed root", e);
        }
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }
}
