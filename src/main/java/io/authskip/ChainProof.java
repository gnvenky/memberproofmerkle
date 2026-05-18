package io.authskip;

import java.util.List;

public record ChainProof(boolean allowed, List<Edge> chain, List<MerkleProof> proofs, String rootHash) {
    public boolean verify() {
        if (!allowed) {
            return chain.isEmpty() && proofs.isEmpty();
        }
        if (chain.size() != proofs.size()) {
            return false;
        }
        for (int i = 0; i < chain.size(); i++) {
            MerkleProof proof = proofs.get(i);
            if (!proof.leafValue().equals(chain.get(i).canonical())) {
                return false;
            }
            if (!proof.rootHash().equals(rootHash) || !proof.verify()) {
                return false;
            }
        }
        return true;
    }
}
