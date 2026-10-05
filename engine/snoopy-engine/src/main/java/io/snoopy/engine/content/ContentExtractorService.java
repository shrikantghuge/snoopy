package io.snoopy.engine.content;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class ContentExtractorService {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractorService.class);
    private final Tika tika = new Tika();

    public String extractText(byte[] content, String mimeType, String filename) {
        if (content == null || content.length == 0) {
            return "";
        }

        // Plain text shortcut for common extensions and text mime types
        if (isTextFormat(mimeType, filename)) {
            return new String(content, StandardCharsets.UTF_8);
        }

        try {
            String extracted = tika.parseToString(new ByteArrayInputStream(content));
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from file {}: {}", filename, e.getMessage());
        }

        // Default fallback to UTF-8 string conversion
        return new String(content, StandardCharsets.UTF_8);
    }

    private boolean isTextFormat(String mimeType, String filename) {
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType.contains("json") || mimeType.contains("yaml") || mimeType.contains("xml"))) {
            return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".txt") || lower.endsWith(".yml") || lower.endsWith(".yaml")
                    || lower.endsWith(".json") || lower.endsWith(".properties") || lower.endsWith(".xml")
                    || lower.endsWith(".md") || lower.endsWith(".py") || lower.endsWith(".java")
                    || lower.endsWith(".go") || lower.endsWith(".js") || lower.endsWith(".ts");
        }
        return false;
    }
}
