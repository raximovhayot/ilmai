package org.aiincubator.ilmai.materials.service;

import lombok.Getter;

@Getter
public class RawMaterial {

    private final byte[] content;
    private final String contentType;
    private final String filename;

    public RawMaterial(byte[] content, String contentType, String filename) {
        this.content = content;
        this.contentType = contentType;
        this.filename = filename;
    }
}
