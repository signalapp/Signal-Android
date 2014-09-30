package org.whispersystems.libaxolotl.groups.state;

import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.groups.ratchet.SenderChainKey;
import org.whispersystems.libaxolotl.groups.ratchet.SenderMessageKey;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libaxolotl.state.StorageProtos.SenderKeyStateStructure;

public class SenderKeyState {

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

    this.senderKeyStateStructure = this.senderKeyStateStructure.toBuilder()
                                                               .addSenderMessageKeys(senderMessageKeyStructure)
                                                               .build();
  }

  public SenderMessageKey removeSenderMessageKey(int iteration) {
    List<SenderKeyStateStructure.SenderMessageKey>     keys     = new LinkedList<>(senderKeyStateStructure.getSenderMessageKeysList());
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
