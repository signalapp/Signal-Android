package org.whispersystems.textsecure.push;

import org.whispersystems.textsecure.util.Base64;

import java.util.LinkedList;
import java.util.List;

public class OutgoingPushMessage implements PushMessage {

  private int                         type;
  private String                      destination;
  private String                      body;
  private List<PushAttachmentPointer> attachments;

  public OutgoingPushMessage(String destination, byte[] body, int type) {
    this.attachments = new LinkedList<PushAttachmentPointer>();
    this.destination = destination;
    this.body        = Base64.encodeBytes(body);
    this.type        = type;
  }

  public OutgoingPushMessage(String destination, byte[] body,
                             List<PushAttachmentPointer> attachments,
                             int type)
  {
    this.destination = destination;
    this.body        = Base64.encodeBytes(body);
    this.attachments = attachments;
    this.type        = type;
  }

  public String getDestination() {
    return destination;
  }

  public String getBody() {
    return body;
  }

  public List<PushAttachmentPointer> getAttachments() {
    return attachments;
  }

  public int getType() {
    return type;
  }
}
