package org.whispersystems.libaxolotl.groups.state;

public interface SenderKeyStore {
  public void storeSenderKey(String senderKeyId, SenderKeyRecord record);
  public SenderKeyRecord loadSenderKey(String senderKeyId);
}
