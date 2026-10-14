package com.example.examplemod.policy;

/** Facts are computed on the server; a request supplies neither membership nor permission. */
public final class AtlasTileAccess {
    private AtlasTileAccess() {}
    public static boolean allowed(boolean authorizedViewer, boolean anchorMatches, boolean dimensionMatches) {
        return authorizedViewer && anchorMatches && dimensionMatches;
    }
}
