package org.whispersystems.textsecure.push;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessage {

  private List<String>                destinations;
  private String                      messageText;
  private List<PushAttachmentPointer> attachments;

  public OutgoingPushMessage(String destination, String messageText) {
    this.destinations = new LinkedList<String>();
    this.attachments  = new LinkedList<PushAttachmentPointer>();
    this.messageText  = messageText;
    this.destinations.add(destination);
  }

  public OutgoingPushMessage(List<String> destinations, String messageText) {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = new LinkedList<PushAttachmentPointer>();
  }

  public OutgoingPushMessage(List<String> destinations, String messageText,
                             List<PushAttachmentPointer> attachments)
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

  public List<PushAttachmentPointer> getAttachments() {
    return attachments;
  }

}
