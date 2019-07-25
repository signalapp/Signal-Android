package org.thoughtcrime.securesms.revealable;

public class RevealExpirationInfo {

  private final long messageId;
  private final long receiveTime;
  private final long revealStartTime;
  private final long revealDuration;

  public RevealExpirationInfo(long messageId, long receiveTime, long revealStartTime, long revealDuration) {
    this.messageId       = messageId;
    this.receiveTime     = receiveTime;
    this.revealStartTime = revealStartTime;
    this.revealDuration  = revealDuration;
  }

  public long getMessageId() {
    return messageId;
  }

  public long getReceiveTime() {
    return receiveTime;
  }

  public long getRevealStartTime() {
    return revealStartTime;
  }

  public long getRevealDuration() {
    return revealDuration;
  }
}
