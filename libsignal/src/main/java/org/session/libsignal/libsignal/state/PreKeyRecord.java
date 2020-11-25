/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.libsignal.state;

import com.google.protobuf.ByteString;

import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.ecc.Curve;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.ecc.ECPrivateKey;
import org.session.libsignal.libsignal.ecc.ECPublicKey;

import java.io.IOException;

import static org.session.libsignal.libsignal.state.StorageProtos.PreKeyRecordStructure;

public class PreKeyRecord {

  private PreKeyRecordStructure structure;

  public PreKeyRecord(int id, ECKeyPair keyPair) {
    this.structure = PreKeyRecordStructure.newBuilder()
                                          .setId(id)
                                          .setPublicKey(ByteString.copyFrom(keyPair.getPublicKey()
                                                                                   .serialize()))
                                          .setPrivateKey(ByteString.copyFrom(keyPair.getPrivateKey()
                                                                                    .serialize()))
                                          .build();
  }

  public PreKeyRecord(byte[] serialized) throws IOException {
    this.structure = PreKeyRecordStructure.parseFrom(serialized);
  }

  public int getId() {
    return this.structure.getId();
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

  public byte[] serialize() {
    return this.structure.toByteArray();
  }
}
