package io.snoopy.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Tenant-scoped HMAC-SHA256 fingerprinting (spec 21 / 45).
 *
 * <p>Plain SHA-256 is deliberately NOT used: low-entropy passwords would be offline-guessable.
 */
public final class Fingerprinter {

    private static final String ALG = "HmacSHA256";
    private final SecretKeySpec key;

    public Fingerprinter(byte[] tenantKey) {
        Objects.requireNonNull(tenantKey, "tenantKey");
        if (tenantKey.length < 32) {
            throw new IllegalArgumentException("tenant HMAC key must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(tenantKey.clone(), ALG);
    }

    /** Credential fingerprint: identifies the secret itself across all systems. */
    public String credentialFingerprint(String secret) {
        return hmac(canonicalize(secret));
    }

    /** Occurrence fingerprint: identifies one specific place a credential was seen. */
    public String occurrenceFingerprint(String credentialFingerprint, String sourceType, String objectId,
                                        String version, String location) {
        return hmac(String.join("\u0000", credentialFingerprint, sourceType, nz(objectId), nz(version), nz(location)));
    }

    /**
     * Deterministic canonicalization: trims whitespace and surrounding quotes; for PEM keys strips all whitespace
     * so the same key wrapped differently (Confluence vs Git) correlates.
     */
    public static String canonicalize(String secret) {
        String s = secret.strip();
        while (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        if (s.contains("PRIVATE KEY")) {
            s = s.replace("\\n", "").replaceAll("\\s+", "");
        }
        return s;
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
