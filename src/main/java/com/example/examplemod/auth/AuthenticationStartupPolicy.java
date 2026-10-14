package com.example.examplemod.auth;

/**
 * Startup policy for production authentication: what a server may do, given its config and world.
 *
 * <p>Pure and side-effect free on purpose. The decision was previously inline in
 * {@code AuthenticationBootstrap.starting}, which made it untestable: every rule could only be
 * observed by booting a real server with a real world and a real backend. Here the whole rule set
 * is a function of {@link Facts}, so it is verified headless — and, more importantly, so the
 * fail-fast guarantee can be read in one place instead of inferred from a chain of {@code throw}s.
 *
 * <p><b>Production authentication is a dedicated-server deployment, and only a dedicated server can
 * ever satisfy its transport preconditions.</b> Two independent reasons, both structural:
 * <ul>
 *   <li>vanilla {@code IntegratedServer} calls {@code setUsesAuthentication(true)} in its
 *       constructor, so an integrated server is permanently online-mode while REQUIRED demands
 *       offline-mode;</li>
 *   <li>the private loopback TCP binding is applied to dedicated servers only.</li>
 * </ul>
 * So an integrated server is never given an {@link Decision#AUTHENTICATED} outcome. Adding one would
 * be a new authenticated path with weaker transport guarantees — that is the opposite of this
 * policy's purpose.
 *
 * <p><b>What is relaxed for integrated servers, and what is not.</b> Only the failure mode is
 * relaxed: an unmarked singleplayer world starts with a local development identity instead of
 * aborting on a missing production configuration. The world marker remains authoritative — an
 * integrated server opening a marked production world is rejected, because the marker exists
 * precisely to stop a production world from running unauthenticated, and singleplayer would be the
 * easiest way to defeat it.
 *
 * <p>Dedicated-server behaviour is byte-identical to the pre-existing rules: same preconditions,
 * same rejections, same marker handling.
 */
public final class AuthenticationStartupPolicy {

    private AuthenticationStartupPolicy() {
    }

    /** What the server is allowed to do with authentication at startup. */
    public enum Decision {
        /** Enforce production authentication. */
        AUTHENTICATED,
        /** No authentication: local development identity only. */
        LOCAL_IDENTITY,
        /** Refuse to start. */
        REJECTED
    }

    /**
     * Everything the decision depends on, already read from the live server.
     *
     * <p>Read once by the caller so this class never touches {@code MinecraftServer}, NeoForge
     * config or the filesystem, and so a test can describe any server exactly.
     *
     * @param dedicated         true for a dedicated server, false for IntegratedServer
     * @param required          configured mode is REQUIRED (as opposed to DISABLED)
     * @param serverId          configured server id, possibly empty
     * @param offlineMode       {@code !server.usesAuthentication()}
     * @param privateTcpBinding this server received the private loopback TCP binding
     * @param worldMarked       the world carries a production authentication marker
     * @param worldMarkerId     the id recorded in the marker, {@code null} when unmarked
     */
    public record Facts(
            boolean dedicated,
            boolean required,
            String serverId,
            boolean offlineMode,
            boolean privateTcpBinding,
            boolean worldMarked,
            String worldMarkerId
    ) {
    }

    /** A decision plus the reason, so a rejection can explain itself and the operator can act. */
    public record Outcome(Decision decision, String reason) {
        public Outcome {
            if (decision == null) {
                throw new IllegalArgumentException("decision");
            }
            if (decision == Decision.REJECTED && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("a rejection must say why");
            }
        }

        static Outcome authenticated() {
            return new Outcome(Decision.AUTHENTICATED, null);
        }

        static Outcome localIdentity(String reason) {
            return new Outcome(Decision.LOCAL_IDENTITY, reason);
        }

        static Outcome rejected(String reason) {
            return new Outcome(Decision.REJECTED, reason);
        }
    }

    /** Server id charset required by the backend contract. */
    public static boolean validServerId(String serverId) {
        return serverId != null && serverId.matches("[a-z0-9_-]{1,64}");
    }

    /**
     * Applies the policy.
     *
     * <p>Order matters: a marked production world is checked against its recorded domain before
     * anything else about REQUIRED is considered, so a misconfigured reload of a production world
     * still fails with the domain mismatch an operator would expect, not with a generic message.
     */
    public static Outcome evaluate(Facts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts");
        }

        if (!facts.dedicated()) {
            /*
             * Singleplayer never authenticates: it cannot meet the transport
             * preconditions, and inventing a weaker authenticated path for it
             * would weaken production guarantees.
             */
            if (facts.worldMarked()) {
                return Outcome.rejected(
                        "Production authentication world cannot be opened on an integrated server");
            }
            return Outcome.localIdentity(
                    "RE:CREATED authentication local identity: integrated server is a singleplayer world");
        }

        if (facts.required()) {
            if (!validServerId(facts.serverId())) {
                return Outcome.rejected(
                        "REQUIRED needs serverId matching [a-z0-9_-]{1,64}");
            }
            if (!facts.offlineMode()) {
                return Outcome.rejected("REQUIRED needs online-mode=false in server.properties");
            }
            if (!facts.privateTcpBinding()) {
                return Outcome.rejected(
                        "REQUIRED needs CCEC_AUTH_TRANSPORT=TLS_GATEWAY private TCP binding");
            }
            /*
             * A marked world whose domain cannot be read is a mismatch, not a
             * pass: "unknown domain" must never authenticate. The live caller
             * normally fails even earlier, because reading a broken marker
             * throws, but the policy must fail closed for any input rather
             * than rely on the caller having already done so.
             */
            if (facts.worldMarked() && (facts.worldMarkerId() == null
                    || !facts.worldMarkerId().equals(facts.serverId()))) {
                return Outcome.rejected("Authentication world domain mismatch");
            }
            return Outcome.authenticated();
        }

        if (facts.worldMarked()) {
            return Outcome.rejected("Production authentication world cannot run DISABLED");
        }
        return Outcome.localIdentity(
                "RE:CREATED authentication DISABLED: local development identity only");
    }
}
