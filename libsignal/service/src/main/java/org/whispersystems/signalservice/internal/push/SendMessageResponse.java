package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SendMessageResponse {

  @JsonProperty
  private boolean needsSync;

  private boolean sentUnidentified;

  public SendMessageResponse() {}

  public SendMessageResponse(boolean needsSync, boolean sentUnidentified) {
    this.needsSync       = needsSync;
    this.sentUnidentified = sentUnidentified;
  }

  public boolean getNeedsSync() {
    return needsSync;
  }

  public boolean sentUnidentified() {
    return sentUnidentified;
  }

  public void setSentUnidentified(boolean value) {
    this.sentUnidentified = value;
  }
}
