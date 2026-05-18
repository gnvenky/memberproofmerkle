# authskip-java

`authskip-java` is a small Java MVP for authenticated membership checks.

It models facts like:

```text
user:alice member team:platform
team:platform member org:acme
taxonomy:sec child-of taxonomy:regulatory
```

The checker can answer whether a subject reaches an object through a relation
chain and return cryptographic inclusion proofs for every hop.

## Design

- `SkipListSet` keeps each membership set ordered and fast to update.
- `MerkleTree` commits to the sorted edge set and creates inclusion proofs.
- `MembershipGraph` finds authorization chains across direct edges.
- `ChainProof` bundles the path, Merkle proofs, and root hash.
- `RootSigner` signs the root hash with Ed25519 so proofs can be verified
  against an authorized issuer key.

This is intentionally an MVP. The skip list is the ordered index layer; the
Merkle tree is the authenticated proof layer. A later version can replace the
Merkle proof layer with a more compact authenticated skip-list proof format.

## How it works

### 1. Populate membership edges

Membership is stored as directed edges:

```java
MembershipGraph graph = new MembershipGraph();
graph.addEdge("user:alice", "member", "team:platform");
graph.addEdge("team:platform", "member", "org:acme");
```

Each edge is stored in two places:

- `MembershipGraph.outgoing`, a graph index used to find authorization chains.
- `AuthenticatedEdgeSet`, an ordered skip-list-backed set used to commit to the
  exact edge set.

For example:

```text
user:alice -> team:platform -> org:acme
```

means Alice is a member of Acme through the `team:platform` chain.

### 2. Commit the edge set to a root hash

Every edge has a canonical form:

```text
subject + separator + relation + separator + object
```

For example:

```text
user:alice member team:platform
```

is represented internally as a stable canonical string. The skip list returns
all edges in sorted order. The Merkle tree hashes each canonical edge as a leaf,
then combines leaves upward until it produces one `root_hash`.

That `root_hash` is a cryptographic commitment to the current membership
database.

### 3. Sign the root hash

The root can be signed by the authority that owns the membership database:

```java
RootSigner signer = RootSigner.create();
SignedRoot signedRoot = signer.sign(graph.rootHash());
```

A verifier can check:

```java
RootSigner.verify(signedRoot, trustedPublicKey);
```

This proves the root hash came from the expected issuer.

### 4. Check membership through a chain

To check whether Alice is a member of Acme:

```java
ChainProof proof = graph.check("user:alice", "member", "org:acme");
```

The graph searches `member` edges and finds a chain:

```text
user:alice member team:platform
team:platform member org:acme
```

The result has `allowed = true` because the subject reaches the requested
object through valid relation hops.

### 5. Produce one proof per edge

There is one shared `root_hash` for the whole membership database, but the
current MVP returns one inclusion proof per edge in the chain.

For this chain:

```text
user:alice member team:platform
team:platform member org:acme
```

the response contains:

```text
root_hash: one shared root for all edges

proofs:
  proof that "user:alice member team:platform" is included under root_hash
  proof that "team:platform member org:acme" is included under root_hash
```

The root hash alone is not enough. It only says that someone committed to some
set of edges. Each inclusion proof shows that a specific edge belongs to that
committed set.

### 6. Verify the proof

For each edge, verification starts with the canonical edge string:

```text
user:alice member team:platform
```

The verifier hashes it as a Merkle leaf:

```text
leaf_hash = H("leaf:user:alice<sep>member<sep>team:platform")
```

The proof contains the sibling hashes needed to climb from that leaf back to the
root:

```text
leaf_hash
  + sibling_1 -> parent_hash
  + sibling_2 -> grandparent_hash
  + sibling_3 -> root_hash
```

Verification recomputes the root:

```text
proof + edge => recomputed_root_hash
```

Then it checks:

```text
recomputed_root_hash == root_hash
root_hash is signed by the trusted issuer
the edge chain connects subject to object
```

So the full authorization statement is:

```text
Alice is authorized because:
1. There is a chain from user:alice to org:acme.
2. Every edge in that chain is proven to exist under the same root_hash.
3. The root_hash was signed by a trusted authority.
```

In short:

```text
populate edges
  -> ordered skip list
  -> Merkle root
  -> signed root
  -> graph chain
  -> per-edge inclusion proofs
  -> verify chain + proofs + signature
```

## Run

In-memory demo:

```bash
mvn package
java -cp target/classes io.authskip.Demo
```

Postgres-backed demo:

```bash
docker compose up -d
mvn exec:java -Dexec.mainClass=io.authskip.DemoPostgres
```

The Postgres container uses:

```text
database: authskip
user: authskip
password: authskip
port: 5432
```

You can override the Java connection settings with:

```bash
export AUTHSKIP_JDBC_URL=jdbc:postgresql://localhost:5432/authskip
export AUTHSKIP_DB_USER=authskip
export AUTHSKIP_DB_PASSWORD=authskip
```

The Postgres demo:

1. Persists membership edges into `membership_edges`.
2. Reloads active edges from Postgres.
3. Rebuilds the in-memory skip-list and graph indexes.
4. Computes a Merkle root from the sorted edge set.
5. Signs the root hash.
6. Persists the root and signature.
7. Generates and persists one proof per active edge in `edge_proofs`.
8. Checks `user:alice member org:acme`.
9. Loads the persisted proofs for the returned chain.
10. Verifies the chain using the persisted proofs.

Persisted proofs are versioned:

```text
edge_proofs.root_version + edge_proofs.edge_id
```

A proof is only valid for the exact root version it was generated under. If the
membership edge set changes, the Merkle root changes and new proofs must be
generated for the new committed version.
