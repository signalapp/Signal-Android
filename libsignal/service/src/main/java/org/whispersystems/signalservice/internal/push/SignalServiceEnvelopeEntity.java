package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignalServiceEnvelopeEntity {
  
  @JsonProperty
  private int type;

  @JsonProperty
  private String relay;

  @JsonProperty
  private long timestamp;

  @JsonProperty
  private String source;

  @JsonProperty
  private String sourceUuid;

  @JsonProperty
  private int sourceDevice;

  @JsonProperty
  private byte[] message;

  @JsonProperty
  private byte[] content;

  @JsonProperty
  private long serverTimestamp;

  @JsonProperty
  private String guid;

  public SignalServiceEnvelopeEntity() {}

  public int getType() {
    return type;
  }

  public String getRelay() {
    return relay;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getSourceE164() {
    return source;
  }

  public String getSourceUuid() {
    return sourceUuid;
  }

  public boolean hasSource() {
    return source != null || sourceUuid != null;
  }

  public int getSourceDevice() {
    return sourceDevice;
  }

  public byte[] getMessage() {
    return message;
  }

  public byte[] getContent() {
    return content;
  }

  public long getServerTimestamp() {
    return serverTimestamp;
  }

  public String getServerUuid() {
    return guid;
  }
}
