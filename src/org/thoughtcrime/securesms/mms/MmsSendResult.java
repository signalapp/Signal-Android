package org.thoughtcrime.securesms.mms;

public class MmsSendResult {

  private final byte[]  messageId;
  private final int     responseStatus;

  public MmsSendResult(byte[] messageId, int responseStatus) {
    this.messageId      = messageId;
    this.responseStatus = responseStatus;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public byte[] getMessageId() {
    return messageId;
  }
}
