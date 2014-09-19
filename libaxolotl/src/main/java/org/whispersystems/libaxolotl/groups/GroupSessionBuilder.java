package org.whispersystems.libaxolotl.groups;

import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.groups.state.SenderKeyRecord;
import org.whispersystems.libaxolotl.groups.state.SenderKeyStore;
import org.whispersystems.libaxolotl.protocol.SenderKeyDistributionMessage;

public class GroupSessionBuilder {

  private final SenderKeyStore senderKeyStore;

  public GroupSessionBuilder(SenderKeyStore senderKeyStore) {
    this.senderKeyStore = senderKeyStore;
  }

  public void process(String sender, SenderKeyDistributionMessage senderKeyDistributionMessage) {
    synchronized (GroupCipher.LOCK) {
      SenderKeyRecord senderKeyRecord = senderKeyStore.loadSenderKey(sender);
      senderKeyRecord.addSenderKeyState(senderKeyDistributionMessage.getId(),
                                        senderKeyDistributionMessage.getIteration(),
                                        senderKeyDistributionMessage.getChainKey(),
                                        senderKeyDistributionMessage.getSignatureKey());
      senderKeyStore.storeSenderKey(sender, senderKeyRecord);
    }
  }

  public SenderKeyDistributionMessage process(String groupId, int keyId, int iteration,
                                              byte[] chainKey, ECKeyPair signatureKey)
  {
    synchronized (GroupCipher.LOCK) {
      SenderKeyRecord senderKeyRecord = senderKeyStore.loadSenderKey(groupId);
      senderKeyRecord.setSenderKeyState(keyId, iteration, chainKey, signatureKey);
      senderKeyStore.storeSenderKey(groupId, senderKeyRecord);

      return new SenderKeyDistributionMessage(keyId, iteration, chainKey, signatureKey.getPublicKey());
    }
  }
}
