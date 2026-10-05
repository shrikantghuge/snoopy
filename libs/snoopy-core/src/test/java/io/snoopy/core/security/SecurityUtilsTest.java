package io.snoopy.core.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @Test
    void fingerprinterProducesConsistentHmac() {
        byte[] key = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        Fingerprinter f = new Fingerprinter(key);

        String fp1 = f.credentialFingerprint("  AKIAIOSFODNN7EXAMPLE  ");
        String fp2 = f.credentialFingerprint("AKIAIOSFODNN7EXAMPLE");

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64);
    }

    @Test
    void secretMaskerMasksCorrectly() {
        assertThat(SecretMasker.mask("AKIAIOSFODNN7EXAMPLE")).isEqualTo("AKIA••••••••MPLE");
        assertThat(SecretMasker.mask("ghp_123456789012345678901234567890123456")).isEqualTo("ghp_••••••••3456");
        assertThat(SecretMasker.mask("short")).isEqualTo("••••••••");
    }

    @Test
    void logRedactorScrubsSecrets() {
        String input = "Found AWS key AKIAIOSFODNN7EXAMPLE in log and password=supersecret123!";
        String redacted = LogRedactor.redact(input);

        assertThat(redacted).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(redacted).doesNotContain("supersecret123!");
        assertThat(redacted).contains("[REDACTED]");
    }
}
