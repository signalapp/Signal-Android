package org.whispersystems.textsecure.push;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessageList {

  private List<OutgoingPushMessage> messages;

  public OutgoingPushMessageList(OutgoingPushMessage message) {
    this.messages = new LinkedList<OutgoingPushMessage>();
    this.messages.add(message);
  }

  public OutgoingPushMessageList(List<OutgoingPushMessage> messages) {
    this.messages = messages;
  }

  public List<OutgoingPushMessage> getMessages() {
    return messages;
  }
}
