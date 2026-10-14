package com.example.examplemod.client.screen;

/** Client affordance only. Claim permissions are still validated by the server. */
final class CityMapClaimAccess {
    private CityMapClaimAccess() {}
    static boolean allowed(boolean hasCity, boolean canManage, boolean rootOpen,
                           boolean workspaceOpen, boolean owningAtlasOpen, boolean atlasReadOnly) {
        return hasCity && canManage && (rootOpen || workspaceOpen || owningAtlasOpen && !atlasReadOnly);
    }
}
