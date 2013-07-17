package org.whispersystems.textsecure.push;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessage {

  private List<String> destinations;
  private String       messageText;
  private List<String> attachments;

  public OutgoingPushMessage(String destination, String messageText) {
    this.destinations = new LinkedList<String>();
    this.attachments  = new LinkedList<String>();
    this.messageText  = messageText;
    this.destinations.add(destination);
  }

  public OutgoingPushMessage(List<String> destinations, String messageText) {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = new LinkedList<String>();
  }

  public OutgoingPushMessage(List<String> destinations, String messageText,
                             List<String> attachments)
  {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = attachments;
  }

  public List<String> getDestinations() {
    return destinations;
  }

  public String getMessageText() {
    return messageText;
  }

  public List<String> getAttachments() {
    return attachments;
  }

}
