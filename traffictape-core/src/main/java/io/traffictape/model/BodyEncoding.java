package io.traffictape.model;

/**
 * How a captured body is represented on the tape.
 * Binary and multipart bodies are omitted rather than serialized as raw bytes.
 */
public enum BodyEncoding {
    JSON,
    TEXT,
    EMPTY,
    OMITTED
}
