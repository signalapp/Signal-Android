package org.whispersystems.textsecure.api.messages;

public class TextSecureSyncContext {

  private final String destination;
  private final long   timestamp;

  public TextSecureSyncContext(String destination, long timestamp) {
    this.destination = destination;
    this.timestamp   = timestamp;
  }

  public String getDestination() {
    return destination;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
