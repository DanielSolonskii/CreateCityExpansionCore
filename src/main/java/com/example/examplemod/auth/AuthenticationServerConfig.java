package com.example.examplemod.auth;

import net.neoforged.neoforge.common.ModConfigSpec;

/** SERVER spec is synced: never put service credentials here. Frozen on server startup. */
public final class AuthenticationServerConfig {
    public enum Mode { REQUIRED, DISABLED }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Mode> MODE;
    public static final ModConfigSpec.ConfigValue<String> BACKEND, SERVER_ID;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("authentication");
        MODE = b.comment("DISABLED is exclusively for a separate local development world.").defineEnum("mode",Mode.REQUIRED);
        BACKEND = b.define("backendBaseUrl", "");
        SERVER_ID = b.define("serverId", "");
        b.pop(); SPEC=b.build();
    }
    private AuthenticationServerConfig() {}
}
