package dev.createserver.backend.account;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class Secrets {
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static String code() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return code.substring(0, 5) + "-" + code.substring(5);
    }
    public static String normalizeCode(String value) {
        if (value == null) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-?[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}")) return null;
        return code.replace("-", "");
    }
    public static String keyedHash(String purpose, String value, String secret) {
        if (secret == null || secret.length() < 32) throw new AccountFailure(503, "VERIFICATION_NOT_CONFIGURED");
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("HMAC unavailable"); }
    }
    private static final SecureRandom RANDOM = new SecureRandom();
    private Secrets() {}
    public static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static boolean equal(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
    public static boolean isToken(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
}
