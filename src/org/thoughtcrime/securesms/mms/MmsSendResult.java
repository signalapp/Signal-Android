package org.thoughtcrime.securesms.mms;

public class MmsSendResult {

  private final byte[]  messageId;
  private final int     responseStatus;
  private final boolean upgradedSecure;


  public MmsSendResult(byte[] messageId, int responseStatus, boolean upgradedSecure) {
    this.messageId      = messageId;
    this.responseStatus = responseStatus;
    this.upgradedSecure = upgradedSecure;
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
