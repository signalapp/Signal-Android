package org.whispersystems.libaxolotl.groups.state;

import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.StorageProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libaxolotl.state.StorageProtos.SenderKeyRecordStructure;

public class SenderKeyRecord {

  private List<SenderKeyState> senderKeyStates = new LinkedList<>();

  public SenderKeyRecord() {}

  public SenderKeyRecord(byte[] serialized) throws IOException {
    SenderKeyRecordStructure senderKeyRecordStructure = SenderKeyRecordStructure.parseFrom(serialized);

    for (StorageProtos.SenderKeyStateStructure structure : senderKeyRecordStructure.getSenderKeyStatesList()) {
      this.senderKeyStates.add(new SenderKeyState(structure));
    }
  }

  public SenderKeyState getSenderKeyState() throws InvalidKeyIdException {
    if (!senderKeyStates.isEmpty()) {
      return senderKeyStates.get(0);
    } else {
      throw new InvalidKeyIdException("No key state in record!");
    }
  }

  public SenderKeyState getSenderKeyState(int keyId) throws InvalidKeyIdException {
    for (SenderKeyState state : senderKeyStates) {
      if (state.getKeyId() == keyId) {
        return state;
      }
    }

    throw new InvalidKeyIdException("No keys for: " + keyId);
  }

  public void addSenderKeyState(int id, int iteration, byte[] chainKey, ECPublicKey signatureKey) {
    senderKeyStates.add(new SenderKeyState(id, iteration, chainKey, signatureKey));
  }

  public void setSenderKeyState(int id, int iteration, byte[] chainKey, ECKeyPair signatureKey) {
    senderKeyStates.clear();
    senderKeyStates.add(new SenderKeyState(id, iteration, chainKey, signatureKey));
  }

  public byte[] serialize() {
    SenderKeyRecordStructure.Builder recordStructure = SenderKeyRecordStructure.newBuilder();

    for (SenderKeyState senderKeyState : senderKeyStates) {
      recordStructure.addSenderKeyStates(senderKeyState.getStructure());
    }

    return recordStructure.build().toByteArray();
  }
}
