/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.state;

import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.StorageProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libsignal.state.StorageProtos.SenderKeyRecordStructure;

/**
 * A durable representation of a set of SenderKeyStates for a specific
 * SenderKeyName.
 *
 * @author Moxie Marlisnpike
 */
public class SenderKeyRecord {

  private static final int MAX_STATES = 5;

  private LinkedList<SenderKeyState> senderKeyStates = new LinkedList<SenderKeyState>();

  public SenderKeyRecord() {}

  public SenderKeyRecord(byte[] serialized) throws IOException {
    SenderKeyRecordStructure senderKeyRecordStructure = SenderKeyRecordStructure.parseFrom(serialized);

    for (StorageProtos.SenderKeyStateStructure structure : senderKeyRecordStructure.getSenderKeyStatesList()) {
      this.senderKeyStates.add(new SenderKeyState(structure));
    }
  }

  public boolean isEmpty() {
    return senderKeyStates.isEmpty();
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
    senderKeyStates.addFirst(new SenderKeyState(id, iteration, chainKey, signatureKey));

    if (senderKeyStates.size() > MAX_STATES) {
      senderKeyStates.removeLast();
    }
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
