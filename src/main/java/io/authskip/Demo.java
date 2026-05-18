package io.authskip;

public final class Demo {
    private Demo() {
    }

    public static void main(String[] args) {
        MembershipGraph graph = new MembershipGraph();
        graph.addEdge("user:alice", "member", "team:platform");
        graph.addEdge("team:platform", "member", "org:acme");
        graph.addEdge("user:bob", "member", "team:data");
        graph.addEdge("team:data", "member", "org:acme");

        ChainProof proof = graph.check("user:alice", "member", "org:acme");
        RootSigner signer = RootSigner.create();
        SignedRoot signedRoot = signer.sign(proof.rootHash());

        System.out.println("allowed: " + proof.allowed());
        System.out.println("root_hash: " + proof.rootHash());
        System.out.println("root_signature_valid: " + RootSigner.verify(signedRoot, signer.publicKey()));
        System.out.println("chain:");
        for (Edge edge : proof.chain()) {
            System.out.println("  - " + edge);
        }
        System.out.println("proof verifies: " + proof.verify());
    }
}
