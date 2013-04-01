package org.thoughtcrime.securesms.gcm;

import java.util.LinkedList;
import java.util.List;

public class OutgoingGcmMessage {

  private List<String> destinations;
  private String       messageText;
  private List<String> attachments;

  public OutgoingGcmMessage(List<String> destinations, String messageText, List<String> attachments) {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = attachments;
  }

  public OutgoingGcmMessage(String destination, String messageText) {
    this.destinations = new LinkedList<String>();
    this.destinations.add(destination);
    this.messageText  = messageText;
    this.attachments  = new LinkedList<String>();
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
