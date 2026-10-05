package io.snoopy.core.security;

/** Produces display-safe masked representations of secrets. Never reveals more than 8 characters. */
public final class SecretMasker {

    private static final String DOTS = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private SecretMasker() {}

    public static String mask(String secret) {
        if (secret == null || secret.isEmpty()) return "";
        String s = secret.strip();
        if (s.contains("PRIVATE KEY")) {
            int end = s.indexOf("-----", 5);
            String header = end > 0 ? s.substring(0, Math.min(s.length(), end + 5)) : "-----BEGIN PRIVATE KEY-----";
            return header + " " + DOTS;
        }
        int schemeIdx = s.indexOf("://");
        int at = s.lastIndexOf('@');
        if (schemeIdx > 0 && at > schemeIdx) {
            // URL with embedded credentials: keep scheme + host, mask userinfo.
            return s.substring(0, schemeIdx + 3) + DOTS + s.substring(at);
        }
        int len = s.length();
        if (len >= 20) return s.substring(0, 4) + DOTS + s.substring(len - 4);
        if (len >= 12) return s.substring(0, 3) + DOTS + s.substring(len - 2);
        if (len >= 6) return s.substring(0, 1) + DOTS;
        return DOTS;
    }
}
