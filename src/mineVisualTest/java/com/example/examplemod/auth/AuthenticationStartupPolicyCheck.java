package com.example.examplemod.auth;

import com.example.examplemod.auth.AuthenticationStartupPolicy.Decision;
import com.example.examplemod.auth.AuthenticationStartupPolicy.Facts;
import com.example.examplemod.auth.AuthenticationStartupPolicy.Outcome;

/**
 * Startup policy for production authentication, verified headless.
 *
 * <p>Two properties are load-bearing and are pinned exhaustively:
 * <ul>
 *   <li><b>dedicated + REQUIRED + unusable configuration is rejected.</b> A production deployment
 *       must never come up half-authenticated, so every missing or contradictory precondition aborts
 *       the boot rather than degrading to a local identity;</li>
 *   <li><b>an integrated server is allowed to start.</b> It can never satisfy REQUIRED's transport
 *       preconditions — vanilla {@code IntegratedServer} is permanently online-mode and never gets
 *       the private loopback binding — so the previous fail-fast made singleplayer unopenable.</li>
 * </ul>
 *
 * <p>The second must not turn into a general relaxation: the world marker is still authoritative, so
 * a marked production world is refused on an integrated server too. That case is checked explicitly
 * below, because it is the one that would actually be a security hole.
 */
public final class AuthenticationStartupPolicyCheck {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static Decision decide(Facts facts) {
        return AuthenticationStartupPolicy.evaluate(facts).decision();
    }

    private static void rejected(Facts facts, String message) {
        Outcome outcome = AuthenticationStartupPolicy.evaluate(facts);
        check(outcome.decision() == Decision.REJECTED, message);
        check(outcome.reason() != null && !outcome.reason().isBlank(),
                "a rejection must explain itself: " + message);
    }

    /** A dedicated server whose REQUIRED preconditions are all satisfied. */
    private static Facts validDedicated(String serverId) {
        return new Facts(true, true, serverId, true, true, false, null);
    }

    public static void main(String[] args) {
        serverId();
        dedicatedRequiredRejectsInvalidConfiguration();
        dedicatedRequiredAcceptsValidConfiguration();
        dedicatedDisabledStillGuardedByMarker();
        dedicatedMarkerDomainPinned();
        integratedServerIsAllowed();
        integratedNeverAuthenticates();
        integratedCannotOpenProductionWorld();
        reasonsExplainEveryRejection();

        System.out.println("PASS: " + checks + " authentication startup policy checks");
    }

    // -------------------------------------------------------------------------- server id

    private static void serverId() {
        check(AuthenticationStartupPolicy.validServerId("recreated-alpha"), "server id rejected");
        check(AuthenticationStartupPolicy.validServerId("a"), "single character server id rejected");
        check(AuthenticationStartupPolicy.validServerId("0-_az"), "underscore/hyphen server id rejected");
        // Lowercase only: the charset is unchanged from the production rule, so a mixed-case id is
        // still refused rather than silently normalised.
        check(!AuthenticationStartupPolicy.validServerId("Recreated-alpha"),
                "uppercase server id accepted");
        for (String invalid : new String[]{null, "", "Recreated", "has space", "a/b",
                "id\n", "§cadmin", "id."}) {
            check(!AuthenticationStartupPolicy.validServerId(invalid),
                    "invalid server id accepted: " + invalid);
        }
        // The length bound is 1..64, unlike the 3..16 display-name rule: a two-character id is a
        // legitimate short domain and must not be rejected by an unrelated limit.
        check(AuthenticationStartupPolicy.validServerId("ab"), "two-character server id rejected");
        check(AuthenticationStartupPolicy.validServerId("a".repeat(64)), "64-character server id rejected");
        check(!AuthenticationStartupPolicy.validServerId("a".repeat(65)), "65-character server id accepted");
    }

    // ------------------------------------------------------- dedicated REQUIRED: fail-fast

    /**
     * The guarantee this task is about: a dedicated server in REQUIRED mode with a broken
     * configuration must refuse to start rather than fall back to a local identity.
     *
     * <p>Each precondition is broken individually so a regression names the precondition it broke.
     */
    private static void dedicatedRequiredRejectsInvalidConfiguration() {
        // No configuration at all — the shipped default is REQUIRED with an empty server id.
        rejected(validDedicated(""), "dedicated REQUIRED with no serverId must be rejected");
        rejected(new Facts(true, true, null, true, true, false, null),
                "dedicated REQUIRED with a null serverId must be rejected");

        // Configured, but the backend would be reached through the public path.
        rejected(new Facts(true, true, "recreated-alpha", false, true, false, null),
                "dedicated REQUIRED with online-mode=true must be rejected");

        // Configured, but the vanilla port is still publicly bound.
        rejected(new Facts(true, true, "recreated-alpha", true, false, false, null),
                "dedicated REQUIRED without the private TCP binding must be rejected");

        // Every precondition broken at once: the ordinary "I forgot to configure it" case.
        rejected(new Facts(true, true, "", false, false, false, null),
                "dedicated REQUIRED with no production configuration must be rejected");

        // A valid serverId alone must never be enough.
        rejected(new Facts(true, true, "recreated-alpha", false, false, false, null),
                "a valid serverId alone must not authenticate a dedicated server");
    }

    private static void dedicatedRequiredAcceptsValidConfiguration() {
        check(decide(validDedicated("recreated-alpha")) == Decision.AUTHENTICATED,
                "dedicated REQUIRED with every precondition satisfied must authenticate");
        check(decide(new Facts(true, true, "recreated-alpha", true, true, true, "recreated-alpha"))
                        == Decision.AUTHENTICATED,
                "a marked production world reopening under its own serverId must authenticate");
    }

    private static void dedicatedDisabledStillGuardedByMarker() {
        check(decide(new Facts(true, false, "", true, true, false, null)) == Decision.LOCAL_IDENTITY,
                "an unmarked development world must still be able to run DISABLED on a dedicated server");
        rejected(new Facts(true, false, "", true, true, true, "recreated-alpha"),
                "a marked production world must not run DISABLED");
    }

    /** A production world is pinned to one domain: the marker must agree with the configured id. */
    private static void dedicatedMarkerDomainPinned() {
        rejected(new Facts(true, true, "recreated-staging", true, true, true, "recreated-alpha"),
                "a production world must not be reopened under a different serverId");
        // An unmarked marker with a null recorded id cannot silently pass either.
        rejected(new Facts(true, true, "recreated-alpha", true, true, true, null),
                "a marker with no recorded id must be treated as a mismatch");
    }

    // ----------------------------------------------------------------- integrated: allowed

    /**
     * The regression this task fixes: singleplayer used to abort on the shipped default
     * configuration, because REQUIRED could never pass on an integrated server.
     */
    private static void integratedServerIsAllowed() {
        // Exactly the shipped default: REQUIRED, no serverId, online-mode, no private binding.
        check(decide(new Facts(false, true, "", false, false, false, null)) == Decision.LOCAL_IDENTITY,
                "an integrated server must start with the default configuration");
        // A half-configured singleplayer world must also start.
        check(decide(new Facts(false, true, "recreated-alpha", false, false, false, null))
                        == Decision.LOCAL_IDENTITY,
                "a singleplayer world with a serverId but no transport must still start");
        // Explicitly disabled, singleplayer: unchanged.
        check(decide(new Facts(false, false, "", false, false, false, null)) == Decision.LOCAL_IDENTITY,
                "a disabled singleplayer world must start");
        // Private binding present or absent changes nothing on integrated.
        check(decide(new Facts(false, true, "", false, true, false, null)) == Decision.LOCAL_IDENTITY,
                "a singleplayer server must start regardless of the TCP binding");
        // No combination of transport facts may turn an integrated server into a rejection.
        for (boolean offline : new boolean[]{true, false}) {
            for (boolean privateTcp : new boolean[]{true, false}) {
                for (String id : new String[]{"", "recreated-alpha"}) {
                    for (boolean required : new boolean[]{true, false}) {
                        check(decide(new Facts(false, required, id, offline, privateTcp, false, null))
                                        == Decision.LOCAL_IDENTITY,
                                "integrated must always start on an unmarked world: offline=" + offline
                                        + " privateTcp=" + privateTcp + " id=" + id + " required=" + required);
                    }
                }
            }
        }
    }

    /**
     * Relaxing singleplayer must not create a weaker authenticated path. Production authentication
     * keeps its private TCP binding and offline-mode guarantees only on dedicated servers, so an
     * integrated server must never be handed {@code AUTHENTICATED} — even if it somehow presents a
     * complete-looking configuration.
     */
    private static void integratedNeverAuthenticates() {
        // A complete-looking configuration on an unmarked singleplayer world: still not authenticated.
        check(decide(new Facts(false, true, "recreated-alpha", true, true, false, null))
                        == Decision.LOCAL_IDENTITY,
                "an integrated server must never be granted an authenticated outcome");
        // Exhaustively: no arrangement of facts may ever produce AUTHENTICATED on an integrated
        // server. A marked world is rejected rather than authenticated (see the production-world
        // case below), so it is excluded here by asserting only that it is never AUTHENTICATED.
        for (boolean offline : new boolean[]{true, false}) {
            for (boolean privateTcp : new boolean[]{true, false}) {
                for (boolean required : new boolean[]{true, false}) {
                    for (boolean marked : new boolean[]{true, false}) {
                        Facts facts = new Facts(false, required, "recreated-alpha", offline, privateTcp,
                                marked, marked ? "recreated-alpha" : null);
                        check(decide(facts) != Decision.AUTHENTICATED,
                                "integrated must never authenticate: offline=" + offline
                                        + " privateTcp=" + privateTcp + " required=" + required
                                        + " marked=" + marked);
                    }
                }
            }
        }
    }

    /**
     * The one integrated case that must still refuse to start.
     *
     * <p>Opening a marked production world in singleplayer would be the easiest possible way to run
     * a production world with no authentication, which is exactly what the marker exists to prevent.
     */
    private static void integratedCannotOpenProductionWorld() {
        rejected(new Facts(false, true, "", false, false, true, "recreated-alpha"),
                "singleplayer must refuse to open a marked production world");
        rejected(new Facts(false, false, "", false, false, true, "recreated-alpha"),
                "singleplayer must refuse a marked production world even with mode DISABLED");
        rejected(new Facts(false, true, "recreated-alpha", true, true, true, "recreated-alpha"),
                "singleplayer must refuse a marked production world even with a complete configuration");
        // A marked world is refused whatever the marker text is, including an unreadable one.
        rejected(new Facts(false, true, "", false, false, true, null),
                "singleplayer must refuse a marked production world with no recorded id");
    }

    /** Every rejection names a concrete cause an operator can act on. */
    private static void reasonsExplainEveryRejection() {
        String serverId = AuthenticationStartupPolicy.evaluate(
                new Facts(true, true, "", true, true, false, null)).reason();
        check(serverId.contains("serverId"), "a missing serverId rejection must mention serverId");
        String offline = AuthenticationStartupPolicy.evaluate(
                new Facts(true, true, "id", false, true, false, null)).reason();
        check(offline.contains("online-mode"), "an online-mode rejection must mention online-mode");
        String transport = AuthenticationStartupPolicy.evaluate(
                new Facts(true, true, "id", true, false, false, null)).reason();
        check(transport.contains("CCEC_AUTH_TRANSPORT"),
                "a missing-binding rejection must name the environment variable");
        String marker = AuthenticationStartupPolicy.evaluate(
                new Facts(true, false, "", true, true, true, "recreated-alpha")).reason();
        check(marker.contains("DISABLED"), "a DISABLED-on-production rejection must mention DISABLED");
        String domain = AuthenticationStartupPolicy.evaluate(
                new Facts(true, true, "other", true, true, true, "recreated-alpha")).reason();
        check(domain.contains("mismatch"), "a domain rejection must say it is a mismatch");
        String integrated = AuthenticationStartupPolicy.evaluate(
                new Facts(false, true, "", false, false, true, "recreated-alpha")).reason();
        check(integrated.contains("integrated"),
                "an integrated-on-production rejection must say the server is integrated");
        // A non-rejection never carries a rejection reason.
        check(AuthenticationStartupPolicy.evaluate(validDedicated("recreated-alpha")).reason() == null,
                "an authenticated outcome must not carry a rejection reason");
        // evaluate() must be total: null facts are a programming error, not a silent allow.
        boolean threw = false;
        try {
            AuthenticationStartupPolicy.evaluate(null);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "null facts must be rejected rather than defaulted");
    }
}
