package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SendMessageResponse {

  @JsonProperty
  private boolean needsSync;

  private boolean sentUnidentfied;

  public SendMessageResponse() {}

  public SendMessageResponse(boolean needsSync, boolean sentUnidentified) {
    this.needsSync       = needsSync;
    this.sentUnidentfied = sentUnidentified;
  }

  public boolean getNeedsSync() {
    return needsSync;
  }

  public boolean sentUnidentified() {
    return sentUnidentfied;
  }

  public void setSentUnidentfied(boolean value) {
    this.sentUnidentfied = value;
  }
}
