package org.thoughtcrime.securesms.revealable;

public class ViewOnceExpirationInfo {

  private final long messageId;
  private final long receiveTime;

  public ViewOnceExpirationInfo(long messageId, long receiveTime) {
    this.messageId       = messageId;
    this.receiveTime     = receiveTime;
  }

  public long getMessageId() {
    return messageId;
  }

  public long getReceiveTime() {
    return receiveTime;
  }
}
