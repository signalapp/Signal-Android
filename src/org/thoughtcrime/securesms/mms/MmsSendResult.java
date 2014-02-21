package org.thoughtcrime.securesms.mms;

public class MmsSendResult {

  private final byte[]  messageId;
  private final int     responseStatus;
  private final boolean upgradedSecure;
  private final boolean push;

  public MmsSendResult(byte[] messageId, int responseStatus, boolean upgradedSecure, boolean push) {
    this.messageId      = messageId;
    this.responseStatus = responseStatus;
    this.upgradedSecure = upgradedSecure;
    this.push           = push;
  }

  public boolean isPush() {
    return push;
  }

  public boolean isUpgradedSecure() {
    return upgradedSecure;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public byte[] getMessageId() {
    return messageId;
  }
}
