package io.snoopy.engine.detection.detectors;

import io.snoopy.core.domain.Severity;
import io.snoopy.engine.detection.spi.DetectorMetadata;
import io.snoopy.engine.detection.spi.SecretDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BuiltInDetectors {

    private BuiltInDetectors() {}

    public static List<SecretDetector> createAll() {
        List<SecretDetector> detectors = new ArrayList<>();

        // 1. AWS Access Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("aws-access-key", "AWS Access Key", "AWS_ACCESS_KEY", "AWS",
                        Severity.CRITICAL, 95, List.of("AKIA", "ASIA", "AGPA", "AIDA", "AROA"), "(?:AKIA|ASIA|AGPA|AIDA|AROA)[A-Z0-9]{16}", 3.0),
                Pattern.compile("\\b((?:AKIA|ASIA|AGPA|AIDA|AROA)[A-Z0-9]{16})\\b")
        ));

        // 2. AWS Secret Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("aws-secret-key", "AWS Secret Access Key", "AWS_SECRET_KEY", "AWS",
                        Severity.CRITICAL, 85, List.of("aws_secret", "aws_access"), "(?i)aws_?(?:secret|access)?[_-]?key\\s*[=:]\\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?", 4.5),
                Pattern.compile("(?i)aws_?(?:secret|access)?[_-]?key\\s*[=:]\\s*['\"]?([A-Za-z0-9/+=]{40})['\"]?")
        ));

        // 3. GitHub Personal Access Token / Fine-grained token
        detectors.add(new PatternDetector(
                new DetectorMetadata("github-token", "GitHub Personal Access Token", "GITHUB_TOKEN", "GitHub",
                        Severity.CRITICAL, 95, List.of("ghp_", "gho_", "ghu_", "ghs_", "ghr_", "github_pat_"),
                        "(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{82}", 3.5),
                Pattern.compile("\\b((?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{82})\\b")
        ));

        // 4. GitLab Personal Access Token
        detectors.add(new PatternDetector(
                new DetectorMetadata("gitlab-token", "GitLab Personal Access Token", "GITLAB_TOKEN", "GitLab",
                        Severity.HIGH, 90, List.of("glpat-"), "glpat-[A-Za-z0-9_\\-]{20,}", 3.5),
                Pattern.compile("\\b(glpat-[A-Za-z0-9_\\-]{20,})\\b")
        ));

        // 5. Google API Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("google-api-key", "Google API Key", "GOOGLE_API_KEY", "Google",
                        Severity.HIGH, 90, List.of("AIza"), "AIza[0-9A-Za-z_\\-]{35}", 3.5),
                Pattern.compile("\\b(AIza[0-9A-Za-z_\\-]{35})\\b")
        ));

        // 6. Slack Token
        detectors.add(new PatternDetector(
                new DetectorMetadata("slack-token", "Slack API Token", "SLACK_TOKEN", "Slack",
                        Severity.HIGH, 95, List.of("xoxb-", "xoxp-", "xoxa-", "xoxr-"), "xox[abpr]-[0-9A-Za-z\\-]{10,}", 3.5),
                Pattern.compile("\\b(xox[abpr]-[0-9A-Za-z\\-]{10,})\\b")
        ));

        // 7. Stripe Secret API Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("stripe-api-key", "Stripe API Key", "STRIPE_API_KEY", "Stripe",
                        Severity.CRITICAL, 95, List.of("sk_live_", "rk_live_"), "(?:sk|rk)_live_[0-9a-zA-Z]{24,}", 3.5),
                Pattern.compile("\\b((?:sk|rk)_live_[0-9a-zA-Z]{24,})\\b")
        ));

        // 8. OpenAI API Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("openai-api-key", "OpenAI API Key", "OPENAI_API_KEY", "OpenAI",
                        Severity.HIGH, 90, List.of("sk-"), "sk-(?:proj-)?[A-Za-z0-9_\\-]{32,}", 3.5),
                Pattern.compile("\\b(sk-(?:proj-)?[A-Za-z0-9_\\-]{32,})\\b")
        ));

        // 9. SendGrid API Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("sendgrid-api-key", "SendGrid API Key", "SENDGRID_API_KEY", "SendGrid",
                        Severity.HIGH, 90, List.of("SG."), "SG\\.[A-Za-z0-9_\\-]{16,}\\.[A-Za-z0-9_\\-]{16,}", 3.5),
                Pattern.compile("\\b(SG\\.[A-Za-z0-9_\\-]{16,}\\.[A-Za-z0-9_\\-]{16,})\\b")
        ));

        // 10. SSH / PEM Private Key
        detectors.add(new PatternDetector(
                new DetectorMetadata("pem-private-key", "PEM Private Key", "PEM_PRIVATE_KEY", "Generic",
                        Severity.CRITICAL, 98, List.of("PRIVATE KEY"), "-----BEGIN[ A-Z0-9]*PRIVATE KEY-----[\\s\\S]*?-----END[ A-Z0-9]*PRIVATE KEY-----", 4.0),
                Pattern.compile("(-----BEGIN[ A-Z0-9]*PRIVATE KEY-----[\\s\\S]*?-----END[ A-Z0-9]*PRIVATE KEY-----)")
        ));

        // 11. JWT Token
        detectors.add(new PatternDetector(
                new DetectorMetadata("jwt-token", "JSON Web Token", "JWT", "Generic",
                        Severity.MEDIUM, 70, List.of("eyJ"), "eyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}", 4.0),
                Pattern.compile("\\b(eyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,})\\b")
        ));

        // 12. URL Credential / Basic Auth
        detectors.add(new PatternDetector(
                new DetectorMetadata("url-credential", "URL Basic Auth Credential", "URL_CREDENTIAL", "Generic",
                        Severity.HIGH, 85, List.of("://"), "[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s:@]+:([^\\s:@]+)@[^\\s/]+", 3.0),
                Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s:@]+:([^\\s:@]+)@[^\\s/]+")
        ));

        // 13. Generic Password in Config / Code
        detectors.add(new PatternDetector(
                new DetectorMetadata("generic-password", "Generic Password Pattern", "GENERIC_PASSWORD", "Generic",
                        Severity.HIGH, 65, List.of("password", "passwd", "pwd"), "(?i)(?:password|passwd|pwd)\\s*[=:]\\s*['\"]?([^'\"\\s;]{8,})['\"]?", 3.5),
                Pattern.compile("(?i)(?:password|passwd|pwd)\\s*[=:]\\s*['\"]?([^'\"\\s;]{8,})['\"]?")
        ));

        // 14. Azure Client Secret
        detectors.add(new PatternDetector(
                new DetectorMetadata("azure-client-secret", "Azure Client Secret", "AZURE_CLIENT_SECRET", "Azure",
                        Severity.CRITICAL, 80, List.of("azure", "client_secret"), "(?i)(?:azure|client)[_-]?secret\\s*[=:]\\s*['\"]?([a-zA-Z0-9~_\\-.]{34,40})['\"]?", 4.0),
                Pattern.compile("(?i)(?:azure|client)[_-]?secret\\s*[=:]\\s*['\"]?([a-zA-Z0-9~_\\-.]{34,40})['\"]?")
        ));

        // 15. npm Access Token
        detectors.add(new PatternDetector(
                new DetectorMetadata("npm-token", "npm Access Token", "NPM_TOKEN", "npm",
                        Severity.HIGH, 90, List.of("npm_"), "npm_[A-Za-z0-9]{36}", 3.5),
                Pattern.compile("\\b(npm_[A-Za-z0-9]{36})\\b")
        ));

        return detectors;
    }
}
