package io.snoopy.engine.remediation;

import io.snoopy.core.domain.SourceType;

import java.util.ArrayList;
import java.util.List;

public record RemediationPlaybook(
        String secretType,
        SourceType sourceType,
        String summary,
        List<RemediationStep> steps
) {
    public static RemediationPlaybook generate(String secretType, SourceType sourceType, String locationUrl) {
        List<RemediationStep> steps = new ArrayList<>();

        // Step 1: Revoke / Contain
        if ("AWS_ACCESS_KEY".equals(secretType) || "AWS_SECRET_KEY".equals(secretType)) {
            steps.add(new RemediationStep(1, "CONTAIN", "Revoke or Inactivate AWS IAM Access Key",
                    "Immediately deactivate or delete the exposed access key in the AWS IAM Console or via AWS CLI.",
                    "aws iam update-access-key --access-key-id <KEY_ID> --status Inactive"));
        } else if ("GITHUB_TOKEN".equals(secretType)) {
            steps.add(new RemediationStep(1, "CONTAIN", "Revoke GitHub Personal Access Token",
                    "Go to GitHub Settings -> Developer Settings -> Personal Access Tokens and revoke the token immediately.",
                    "https://github.com/settings/tokens"));
        } else if ("PEM_PRIVATE_KEY".equals(secretType)) {
            steps.add(new RemediationStep(1, "CONTAIN", "Revoke and Rotate SSH/PEM Key Pair",
                    "Remove the public key from authorized_keys on all target servers, rotate SSL certificates, and issue a new key pair.",
                    "ssh-keygen -t ed25519 -C 'rotated-key'"));
        } else {
            steps.add(new RemediationStep(1, "CONTAIN", "Rotate Exposed Credential Immediately",
                    "Inactivate the exposed secret in the provider's management console or API dashboard.",
                    "N/A"));
        }

        // Step 2: Assess Impact
        steps.add(new RemediationStep(2, "ASSESS", "Check Security & Audit Logs",
                "Review provider audit logs (e.g. AWS CloudTrail, GitHub Audit Log, Access Logs) for unauthorized activity during the exposure window.",
                "N/A"));

        // Step 3: Cleanup Source System
        if (sourceType == SourceType.GITHUB || sourceType == SourceType.FILESYSTEM) {
            steps.add(new RemediationStep(3, "CLEANUP", "Purge Secret from Git History",
                    "Use git-filter-repo or BFG Repo-Cleaner to completely remove the secret from all branches, tags, and commits.",
                    "git filter-repo --invert-paths --path <PATH_TO_FILE> --force"));
        } else if (sourceType == SourceType.JIRA_CLOUD) {
            steps.add(new RemediationStep(3, "CLEANUP", "Clean Jira Field, Comment, or Attachment",
                    "Update the issue field or delete the comment/attachment in Jira. Note: Jira issue changelog history remains visible to admins, so rotation is mandatory.",
                    locationUrl != null ? locationUrl : "Jira Issue"));
        } else if (sourceType == SourceType.CONFLUENCE_CLOUD) {
            steps.add(new RemediationStep(3, "CLEANUP", "Purge Confluence Page Versions",
                    "Edit the Confluence page to remove the secret, then navigate to Page History and delete the specific page versions containing the secret.",
                    locationUrl != null ? locationUrl : "Confluence Page History"));
        }

        // Step 4: Prevention
        steps.add(new RemediationStep(4, "PREVENT", "Store in Secret Manager & Enable Pre-Commit Hooks",
                "Move secret to HashiCorp Vault, AWS Secrets Manager, or GCP Secret Manager. Install pre-commit secret scanning hooks.",
                "gitleaks protect --staged"));

        return new RemediationPlaybook(secretType, sourceType,
                "Immediate 4-step remediation protocol for exposed " + secretType + " in " + sourceType, steps);
    }
}
