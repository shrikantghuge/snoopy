package io.snoopy.core.security;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort scrubbing of secrets from log messages (threat T2). Used by the application's log converter as a
 * defence-in-depth layer; code must still never log secrets deliberately.
 */
public final class LogRedactor {

    private static final String R = "[REDACTED]";

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("-----BEGIN[ A-Z0-9]*PRIVATE KEY-----[\\s\\S]*?(-----END[ A-Z0-9]*PRIVATE KEY-----|$)"),
            Pattern.compile("\\b(AKIA|ASIA|AGPA|AIDA|AROA)[A-Z0-9]{16}\\b"),
            Pattern.compile("\\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\\b"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{50,}\\b"),
            Pattern.compile("\\bglpat-[A-Za-z0-9_\\-]{20,}\\b"),
            Pattern.compile("\\bxox[abposr]-[A-Za-z0-9-]{10,}\\b"),
            Pattern.compile("\\b(sk|rk)_(live|test)_[A-Za-z0-9]{16,}\\b"),
            Pattern.compile("\\bsk-(proj-)?[A-Za-z0-9_\\-]{20,}\\b"),
            Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{35}\\b"),
            Pattern.compile("\\bSG\\.[A-Za-z0-9_\\-]{16,}\\.[A-Za-z0-9_\\-]{16,}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\b"),
            Pattern.compile("(?i)(bearer|basic)\\s+[A-Za-z0-9._~+/=\\-]{12,}"),
            Pattern.compile("(?i)([a-z][a-z0-9+.\\-]*://)[^\\s:/@]+:[^\\s@/]+@")
    );

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?key|client[_-]?secret|authorization)\\s*[=:]\\s*)(\"?)([^\\s\"',;]{4,})");

    private LogRedactor() {}

    public static String redact(String message) {
        if (message == null || message.isEmpty()) return message;
        String out = message;
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(out);
            if (m.find()) {
                if (p.pattern().startsWith("(?i)([a-z]")) {
                    out = m.replaceAll("$1" + R + "@");
                } else {
                    out = m.replaceAll(R);
                }
            }
        }
        Matcher kv = KEY_VALUE.matcher(out);
        if (kv.find()) {
            out = kv.replaceAll("$1$2" + R);
        }
        return out;
    }
}
