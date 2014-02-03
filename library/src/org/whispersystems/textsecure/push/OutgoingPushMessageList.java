package org.whispersystems.textsecure.push;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessageList {

  private String destination;

  private String relay;

  private List<OutgoingPushMessage> messages;

  public OutgoingPushMessageList(String destination, String relay, List<OutgoingPushMessage> messages) {
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
}
