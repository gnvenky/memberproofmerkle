package io.authskip.store;

import io.authskip.SignedRoot;

public record RootCommit(long version, SignedRoot signedRoot) {
}
