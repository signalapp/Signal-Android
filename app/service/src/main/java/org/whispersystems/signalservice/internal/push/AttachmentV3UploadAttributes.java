package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public final class AttachmentV3UploadAttributes {
    @JsonProperty
    private int cdn;

    @JsonProperty
    private String key;

    @JsonProperty
    private Map<String, String> headers;

    @JsonProperty
    private String signedUploadLocation;

    public AttachmentV3UploadAttributes() {
    }

    public int getCdn() {
        return cdn;
    }

    public String getKey() {
        return key;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getSignedUploadLocation() {
        return signedUploadLocation;
    }
}
