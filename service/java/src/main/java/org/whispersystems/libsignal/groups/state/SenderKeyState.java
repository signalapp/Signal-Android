/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.groups.state;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.ratchet.SenderChainKey;
import org.whispersystems.libsignal.groups.ratchet.SenderMessageKey;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libsignal.state.StorageProtos.SenderKeyStateStructure;

/**
 * Represents the state of an individual SenderKey ratchet.
 *
 * @author Moxie Marlinspike
 */
public class SenderKeyState {

  private static final int MAX_MESSAGE_KEYS = 2000;

  private SenderKeyStateStructure senderKeyStateStructure;

  public SenderKeyState(int id, int iteration, byte[] chainKey, ECPublicKey signatureKey) {
    this(id, iteration, chainKey, signatureKey, Optional.<ECPrivateKey>absent());
  }

  public SenderKeyState(int id, int iteration, byte[] chainKey, ECKeyPair signatureKey) {
    this(id, iteration, chainKey, signatureKey.getPublicKey(), Optional.of(signatureKey.getPrivateKey()));
  }

  private SenderKeyState(int id, int iteration, byte[] chainKey,
                        ECPublicKey signatureKeyPublic,
                        Optional<ECPrivateKey> signatureKeyPrivate)
  {
    SenderKeyStateStructure.SenderChainKey senderChainKeyStructure =
        SenderKeyStateStructure.SenderChainKey.newBuilder()
                                              .setIteration(iteration)
                                              .setSeed(ByteString.copyFrom(chainKey))
                                              .build();

    SenderKeyStateStructure.SenderSigningKey.Builder signingKeyStructure =
        SenderKeyStateStructure.SenderSigningKey.newBuilder()
                                                .setPublic(ByteString.copyFrom(signatureKeyPublic.serialize()));

    if (signatureKeyPrivate.isPresent()) {
      signingKeyStructure.setPrivate(ByteString.copyFrom(signatureKeyPrivate.get().serialize()));
    }

    this.senderKeyStateStructure = SenderKeyStateStructure.newBuilder()
                                                          .setSenderKeyId(id)
                                                          .setSenderChainKey(senderChainKeyStructure)
                                                          .setSenderSigningKey(signingKeyStructure)
                                                          .build();
  }

  public SenderKeyState(SenderKeyStateStructure senderKeyStateStructure) {
    this.senderKeyStateStructure = senderKeyStateStructure;
  }

  public int getKeyId() {
    return senderKeyStateStructure.getSenderKeyId();
  }

  public SenderChainKey getSenderChainKey() {
    return new SenderChainKey(senderKeyStateStructure.getSenderChainKey().getIteration(),
                              senderKeyStateStructure.getSenderChainKey().getSeed().toByteArray());
  }

  public void setSenderChainKey(SenderChainKey chainKey) {
    SenderKeyStateStructure.SenderChainKey senderChainKeyStructure =
        SenderKeyStateStructure.SenderChainKey.newBuilder()
                                              .setIteration(chainKey.getIteration())
                                              .setSeed(ByteString.copyFrom(chainKey.getSeed()))
                                              .build();

    this.senderKeyStateStructure = senderKeyStateStructure.toBuilder()
                                                          .setSenderChainKey(senderChainKeyStructure)
                                                          .build();
  }

  public ECPublicKey getSigningKeyPublic() throws InvalidKeyException {
    return Curve.decodePoint(senderKeyStateStructure.getSenderSigningKey()
                                                    .getPublic()
                                                    .toByteArray(), 0);
  }

  public ECPrivateKey getSigningKeyPrivate() {
    return Curve.decodePrivatePoint(senderKeyStateStructure.getSenderSigningKey()
                                                           .getPrivate().toByteArray());
  }

  public boolean hasSenderMessageKey(int iteration) {
    for (SenderKeyStateStructure.SenderMessageKey senderMessageKey : senderKeyStateStructure.getSenderMessageKeysList()) {
      if (senderMessageKey.getIteration() == iteration) return true;
    }

    return false;
  }

  public void addSenderMessageKey(SenderMessageKey senderMessageKey) {
    SenderKeyStateStructure.SenderMessageKey senderMessageKeyStructure =
        SenderKeyStateStructure.SenderMessageKey.newBuilder()
                                                .setIteration(senderMessageKey.getIteration())
                                                .setSeed(ByteString.copyFrom(senderMessageKey.getSeed()))
                                                .build();

    SenderKeyStateStructure.Builder builder = this.senderKeyStateStructure.toBuilder();

    builder.addSenderMessageKeys(senderMessageKeyStructure);

    if (builder.getSenderMessageKeysCount() > MAX_MESSAGE_KEYS) {
      builder.removeSenderMessageKeys(0);
    }

    this.senderKeyStateStructure = builder.build();
  }

  public SenderMessageKey removeSenderMessageKey(int iteration) {
    List<SenderKeyStateStructure.SenderMessageKey>     keys     = new LinkedList<SenderKeyStateStructure.SenderMessageKey>(senderKeyStateStructure.getSenderMessageKeysList());
    Iterator<SenderKeyStateStructure.SenderMessageKey> iterator = keys.iterator();

    SenderKeyStateStructure.SenderMessageKey result = null;

    while (iterator.hasNext()) {
      SenderKeyStateStructure.SenderMessageKey senderMessageKey = iterator.next();

      if (senderMessageKey.getIteration() == iteration) {
        result = senderMessageKey;
        iterator.remove();
        break;
      }
    }

    this.senderKeyStateStructure = this.senderKeyStateStructure.toBuilder()
                                                               .clearSenderMessageKeys()
                                                               .addAllSenderMessageKeys(keys)
                                                               .build();

    if (result != null) {
      return new SenderMessageKey(result.getIteration(), result.getSeed().toByteArray());
    } else {
      return null;
    }
  }

  public SenderKeyStateStructure getStructure() {
    return senderKeyStateStructure;
  }
}
