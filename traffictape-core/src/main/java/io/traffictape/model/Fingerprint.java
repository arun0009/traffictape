package io.traffictape.model;

/**
 * Stable id plus a human-readable label. Literal IDs and secrets never appear here.
 */
public record Fingerprint(String id, String label) {
}
