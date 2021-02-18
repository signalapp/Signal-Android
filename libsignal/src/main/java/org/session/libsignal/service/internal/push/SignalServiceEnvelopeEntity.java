package org.session.libsignal.service.internal.push;

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
  private int sourceDevice;

  @JsonProperty
  private byte[] content;

  @JsonProperty
  private long serverTimestamp;

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

  public String getSource() {
    return source;
  }

  public int getSourceDevice() {
    return sourceDevice;
  }

  public byte[] getContent() {
    return content;
  }

  public long getServerTimestamp() {
    return serverTimestamp;
  }
}
