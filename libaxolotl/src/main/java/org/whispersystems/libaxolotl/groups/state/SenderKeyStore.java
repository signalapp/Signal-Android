package org.whispersystems.libaxolotl.groups.state;

import org.whispersystems.libaxolotl.groups.state.SenderKeyRecord;

public interface SenderKeyStore {
  public void storeSenderKey(String senderKeyId, SenderKeyRecord record);
  public SenderKeyRecord loadSenderKey(String senderKeyId);
}
