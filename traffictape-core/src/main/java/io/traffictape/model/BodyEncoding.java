package io.traffictape.model;

/**
 * How a captured body is represented in the corpus.
 * Binary and multipart bodies are omitted rather than serialized as raw bytes.
 */
public enum BodyEncoding {
    JSON,
    TEXT,
    EMPTY,
    OMITTED
}
