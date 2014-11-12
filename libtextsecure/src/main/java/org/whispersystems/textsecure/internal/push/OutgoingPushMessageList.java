package org.whispersystems.textsecure.internal.push;

import java.util.List;

public class OutgoingPushMessageList {

  private String destination;

  private String relay;

  private long timestamp;

  private List<OutgoingPushMessage> messages;

  public OutgoingPushMessageList(String destination, long timestamp, String relay,
                                 List<OutgoingPushMessage> messages)
  {
    this.timestamp   = timestamp;
    this.destination = destination;
    this.relay       = relay;
    this.messages    = messages;
  }

  public String getDestination() {
    return destination;
  }

  public List<OutgoingPushMessage> getMessages() {
    return messages;
  }

  public String getRelay() {
    return relay;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
