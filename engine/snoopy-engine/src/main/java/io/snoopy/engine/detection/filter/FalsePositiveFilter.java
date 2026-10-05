package io.snoopy.engine.detection.filter;

import io.snoopy.engine.detection.spi.SecretCandidate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class FalsePositiveFilter {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "your_api_key", "<your_api_key>", "your_password", "your_secret", "changeme",
            "xxxx", "xxxxxxxx", "********", "password123", "secret123", "example_token",
            "insert_token_here", "dummy_key", "fake_key", "test_key", "todo_set_token"
    );

    private static final List<Pattern> TEMPLATE_PATTERNS = List.of(
            Pattern.compile("^\\$\\{.*\\}$"),
            Pattern.compile("^<.*>$"),
            Pattern.compile("^(?i)example\\.(com|org|net)$"),
            Pattern.compile("^(?i)localhost(:\\d+)?$")
    );

    private FalsePositiveFilter() {}

    public static boolean isFalsePositive(SecretCandidate candidate) {
        String val = candidate.matchedValue().trim();
        String lower = val.toLowerCase(Locale.ROOT);

        if (PLACEHOLDERS.contains(lower)) {
            return true;
        }

        for (Pattern p : TEMPLATE_PATTERNS) {
            if (p.matcher(val).matches()) {
                return true;
            }
        }

        // Low entropy check for generic passwords (e.g. "password", "aaaaaaaa")
        if ("GENERIC_PASSWORD".equals(candidate.secretType()) && candidate.entropy() < 2.5) {
            return true;
        }

        return false;
    }
}
