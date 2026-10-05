package io.snoopy.engine.detection.spi;

import io.snoopy.core.spi.connector.ContentPayload;

import java.util.List;

public interface SecretDetector {
    DetectorMetadata getMetadata();
    List<SecretCandidate> detect(ContentPayload payload, String text);
}
