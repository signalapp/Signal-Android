/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.state;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;

import java.io.IOException;

import static org.whispersystems.libsignal.state.StorageProtos.SignedPreKeyRecordStructure;

public class SignedPreKeyRecord {

  private SignedPreKeyRecordStructure structure;

  public SignedPreKeyRecord(int id, long timestamp, ECKeyPair keyPair, byte[] signature) {
    this.structure = SignedPreKeyRecordStructure.newBuilder()
                                                .setId(id)
                                                .setPublicKey(ByteString.copyFrom(keyPair.getPublicKey()
                                                                                         .serialize()))
                                                .setPrivateKey(ByteString.copyFrom(keyPair.getPrivateKey()
                                                                                          .serialize()))
                                                .setSignature(ByteString.copyFrom(signature))
                                                .setTimestamp(timestamp)
                                                .build();
  }

  public SignedPreKeyRecord(byte[] serialized) throws IOException {
    this.structure = SignedPreKeyRecordStructure.parseFrom(serialized);
  }

  public int getId() {
    return this.structure.getId();
  }

  public long getTimestamp() {
    return this.structure.getTimestamp();
  }

  public ECKeyPair getKeyPair() {
    try {
      ECPublicKey publicKey = Curve.decodePoint(this.structure.getPublicKey().toByteArray(), 0);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(this.structure.getPrivateKey().toByteArray());

      return new ECKeyPair(publicKey, privateKey);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] getSignature() {
    return this.structure.getSignature().toByteArray();
  }

  public byte[] serialize() {
    return this.structure.toByteArray();
  }
}
