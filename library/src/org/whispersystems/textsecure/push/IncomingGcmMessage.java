package org.whispersystems.textsecure.push;

import java.util.List;

public class IncomingGcmMessage {

  private String       source;
  private List<String> destinations;
  private String       messageText;
  private List<String> attachments;
  private long         timestamp;

  public IncomingGcmMessage(String source, List<String> destinations, String messageText, List<String> attachments, long timestamp) {
    this.source       = source;
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = attachments;
    this.timestamp    = timestamp;
  }

  public long getTimestampMillis() {
    return timestamp;
  }

  public String getSource() {
    return source;
  }

  public List<String> getAttachments() {
    return attachments;
  }

  public String getMessageText() {
    return messageText;
  }

  public List<String> getDestinations() {
    return destinations;
  }

}
