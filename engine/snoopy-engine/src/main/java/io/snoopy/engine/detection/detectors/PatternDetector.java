package io.snoopy.engine.detection.detectors;

import io.snoopy.core.domain.Severity;
import io.snoopy.core.spi.connector.ContentPayload;
import io.snoopy.engine.detection.spi.DetectorMetadata;
import io.snoopy.engine.detection.spi.SecretCandidate;
import io.snoopy.engine.detection.spi.SecretDetector;
import io.snoopy.engine.detection.util.EntropyCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternDetector implements SecretDetector {

    private final DetectorMetadata metadata;
    private final Pattern pattern;

    public PatternDetector(DetectorMetadata metadata, Pattern pattern) {
        this.metadata = metadata;
        this.pattern = pattern;
    }

    @Override
    public DetectorMetadata getMetadata() {
        return metadata;
    }

    @Override
    public List<SecretCandidate> detect(ContentPayload payload, String text) {
        List<SecretCandidate> candidates = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return candidates;
        }

        // Quick keyword check optimization if keywords exist
        if (metadata.keywords() != null && !metadata.keywords().isEmpty()) {
            boolean foundKey = false;
            String lower = text.toLowerCase();
            for (String kw : metadata.keywords()) {
                if (lower.contains(kw.toLowerCase())) {
                    foundKey = true;
                    break;
                }
            }
            if (!foundKey) {
                return candidates;
            }
        }

        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            if (match == null || match.isBlank()) continue;

            int start = matcher.start();
            int end = matcher.end();

            // Snippet context calculation (30 chars before and after)
            int snippetStart = Math.max(0, start - 30);
            int snippetEnd = Math.min(text.length(), end + 30);
            String snippet = text.substring(snippetStart, snippetEnd).replace('\n', ' ');

            // Line number calculation
            int lineNumber = 1;
            for (int i = 0; i < start; i++) {
                if (text.charAt(i) == '\n') lineNumber++;
            }
            if (payload.lineNumbers() != null && lineNumber <= payload.lineNumbers().size()) {
                lineNumber = payload.lineNumbers().get(lineNumber - 1);
            }

            double entropy = EntropyCalculator.calculateEntropy(match);

            candidates.add(new SecretCandidate(
                    metadata.id(),
                    metadata.secretType(),
                    metadata.provider(),
                    start,
                    end,
                    lineNumber,
                    match,
                    snippet,
                    metadata.defaultConfidence(),
                    metadata.defaultSeverity(),
                    entropy,
                    Map.of("rule", metadata.id())
            ));
        }

        return candidates;
    }
}
