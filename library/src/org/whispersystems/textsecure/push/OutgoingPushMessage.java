package org.whispersystems.textsecure.push;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessage {

  public static final int TYPE_MESSAGE_PLAINTEXT     = 0;
  public static final int TYPE_MESSAGE_CIPHERTEXT    = 1;
  public static final int TYPE_MESSAGE_KEY_EXCHANGE  = 2;
  public static final int TYPE_MESSAGE_PREKEY_BUNDLE = 3;

  private int                         type;
  private List<String>                destinations;
  private String                      messageText;
  private List<PushAttachmentPointer> attachments;

  public OutgoingPushMessage(String destination, String messageText, int type) {
    this.destinations = new LinkedList<String>();
    this.attachments  = new LinkedList<PushAttachmentPointer>();
    this.messageText  = messageText;
    this.destinations.add(destination);
    this.type         = type;
  }

  public OutgoingPushMessage(List<String> destinations, String messageText, int type) {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = new LinkedList<PushAttachmentPointer>();
    this.type         = type;
  }

  public OutgoingPushMessage(List<String> destinations, String messageText,
                             List<PushAttachmentPointer> attachments, int type)
  {
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = attachments;
    this.type         = type;
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

  public int getType() {
    return type;
  }
}
