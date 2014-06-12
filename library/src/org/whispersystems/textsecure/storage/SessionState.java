package org.whispersystems.textsecure.storage;

import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.ratchet.ChainKey;
import org.whispersystems.textsecure.crypto.ratchet.MessageKeys;
import org.whispersystems.textsecure.crypto.ratchet.RootKey;
import org.whispersystems.textsecure.storage.StorageProtos.SessionStructure.Chain;
import org.whispersystems.textsecure.storage.StorageProtos.SessionStructure.PendingKeyExchange;
import org.whispersystems.textsecure.storage.StorageProtos.SessionStructure.PendingPreKey;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import static org.whispersystems.textsecure.storage.StorageProtos.SessionStructure;

public class SessionState {

  private SessionStructure sessionStructure;

  public SessionState(SessionStructure sessionStructure) {
    this.sessionStructure = sessionStructure;
  }

  public SessionStructure getStructure() {
    return sessionStructure;
  }

  public void setNeedsRefresh(boolean needsRefresh) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setNeedsRefresh(needsRefresh)
                                                 .build();
  }

  public boolean getNeedsRefresh() {
    return this.sessionStructure.getNeedsRefresh();
  }

  public void setSessionVersion(int version) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setSessionVersion(version)
                                                 .build();
  }

  public int getSessionVersion() {
    return this.sessionStructure.getSessionVersion();
  }

  public void setRemoteIdentityKey(IdentityKey identityKey) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setRemoteIdentityPublic(ByteString.copyFrom(identityKey.serialize()))
                                                 .build();
  }

  public void setLocalIdentityKey(IdentityKey identityKey) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setLocalIdentityPublic(ByteString.copyFrom(identityKey.serialize()))
                                                 .build();
  }

  public IdentityKey getRemoteIdentityKey() {
    try {
      if (!this.sessionStructure.hasRemoteIdentityPublic()) {
        return null;
      }

      return new IdentityKey(this.sessionStructure.getRemoteIdentityPublic().toByteArray(), 0);
    } catch (InvalidKeyException e) {
      Log.w("SessionRecordV2", e);
      return null;
    }
  }

  public IdentityKey getLocalIdentityKey() {
    try {
      return new IdentityKey(this.sessionStructure.getLocalIdentityPublic().toByteArray(), 0);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public int getPreviousCounter() {
    return sessionStructure.getPreviousCounter();
  }

  public void setPreviousCounter(int previousCounter) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setPreviousCounter(previousCounter)
                                                 .build();
  }

  public RootKey getRootKey() {
    return new RootKey(this.sessionStructure.getRootKey().toByteArray());
  }

  public void setRootKey(RootKey rootKey) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setRootKey(ByteString.copyFrom(rootKey.getKeyBytes()))
                                                 .build();
  }

  public ECPublicKey getSenderEphemeral() {
    try {
      return Curve.decodePoint(sessionStructure.getSenderChain().getSenderEphemeral().toByteArray(), 0);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public ECKeyPair getSenderEphemeralPair() {
    ECPublicKey  publicKey  = getSenderEphemeral();
    ECPrivateKey privateKey = Curve.decodePrivatePoint(sessionStructure.getSenderChain()
                                                                       .getSenderEphemeralPrivate()
                                                                       .toByteArray());

    return new ECKeyPair(publicKey, privateKey);
  }

  public boolean hasReceiverChain(ECPublicKey senderEphemeral) {
    return getReceiverChain(senderEphemeral) != null;
  }

  public boolean hasSenderChain() {
    return sessionStructure.hasSenderChain();
  }

  private Pair<Chain,Integer> getReceiverChain(ECPublicKey senderEphemeral) {
    List<Chain> receiverChains = sessionStructure.getReceiverChainsList();
    int         index          = 0;

    for (Chain receiverChain : receiverChains) {
      try {
        ECPublicKey chainSenderEphemeral = Curve.decodePoint(receiverChain.getSenderEphemeral().toByteArray(), 0);

        if (chainSenderEphemeral.equals(senderEphemeral)) {
          return new Pair<Chain,Integer>(receiverChain,index);
        }
      } catch (InvalidKeyException e) {
        Log.w("SessionRecordV2", e);
      }

      index++;
    }

    return null;
  }

  public ChainKey getReceiverChainKey(ECPublicKey senderEphemeral) {
    Pair<Chain,Integer> receiverChainAndIndex = getReceiverChain(senderEphemeral);
    Chain               receiverChain         = receiverChainAndIndex.first;

    if (receiverChain == null) {
      return null;
    } else {
      return new ChainKey(receiverChain.getChainKey().getKey().toByteArray(),
                          receiverChain.getChainKey().getIndex());
    }
  }

  public void addReceiverChain(ECPublicKey senderEphemeral, ChainKey chainKey) {
    Chain.ChainKey chainKeyStructure = Chain.ChainKey.newBuilder()
                                                     .setKey(ByteString.copyFrom(chainKey.getKey()))
                                                     .setIndex(chainKey.getIndex())
                                                     .build();

    Chain chain = Chain.newBuilder()
                       .setChainKey(chainKeyStructure)
                       .setSenderEphemeral(ByteString.copyFrom(senderEphemeral.serialize()))
                       .build();

    this.sessionStructure = this.sessionStructure.toBuilder().addReceiverChains(chain).build();

    if (this.sessionStructure.getReceiverChainsList().size() > 5) {
      this.sessionStructure = this.sessionStructure.toBuilder()
                                                   .removeReceiverChains(0)
                                                   .build();
    }
  }

  public void setSenderChain(ECKeyPair senderEphemeralPair, ChainKey chainKey) {
    Chain.ChainKey chainKeyStructure = Chain.ChainKey.newBuilder()
                                                     .setKey(ByteString.copyFrom(chainKey.getKey()))
                                                     .setIndex(chainKey.getIndex())
                                                     .build();

    Chain senderChain = Chain.newBuilder()
                             .setSenderEphemeral(ByteString.copyFrom(senderEphemeralPair.getPublicKey().serialize()))
                             .setSenderEphemeralPrivate(ByteString.copyFrom(senderEphemeralPair.getPrivateKey().serialize()))
                             .setChainKey(chainKeyStructure)
                             .build();

    this.sessionStructure = this.sessionStructure.toBuilder().setSenderChain(senderChain).build();
  }

  public ChainKey getSenderChainKey() {
    Chain.ChainKey chainKeyStructure = sessionStructure.getSenderChain().getChainKey();
    return new ChainKey(chainKeyStructure.getKey().toByteArray(), chainKeyStructure.getIndex());
  }


  public void setSenderChainKey(ChainKey nextChainKey) {
    Chain.ChainKey chainKey = Chain.ChainKey.newBuilder()
                                            .setKey(ByteString.copyFrom(nextChainKey.getKey()))
                                            .setIndex(nextChainKey.getIndex())
                                            .build();

    Chain chain = sessionStructure.getSenderChain().toBuilder()
                                  .setChainKey(chainKey).build();

    this.sessionStructure = this.sessionStructure.toBuilder().setSenderChain(chain).build();
  }

  public boolean hasMessageKeys(ECPublicKey senderEphemeral, int counter) {
    Pair<Chain,Integer> chainAndIndex = getReceiverChain(senderEphemeral);
    Chain               chain         = chainAndIndex.first;

    if (chain == null) {
      return false;
    }

    List<Chain.MessageKey> messageKeyList = chain.getMessageKeysList();

    for (Chain.MessageKey messageKey : messageKeyList) {
      if (messageKey.getIndex() == counter) {
        return true;
      }
    }

    return false;
  }

  public MessageKeys removeMessageKeys(ECPublicKey senderEphemeral, int counter) {
    Pair<Chain,Integer> chainAndIndex = getReceiverChain(senderEphemeral);
    Chain               chain         = chainAndIndex.first;

    if (chain == null) {
      return null;
    }

    List<Chain.MessageKey>     messageKeyList     = new LinkedList<Chain.MessageKey>(chain.getMessageKeysList());
    Iterator<Chain.MessageKey> messageKeyIterator = messageKeyList.iterator();
    MessageKeys                result             = null;

    while (messageKeyIterator.hasNext()) {
      Chain.MessageKey messageKey = messageKeyIterator.next();

      if (messageKey.getIndex() == counter) {
        result = new MessageKeys(new SecretKeySpec(messageKey.getCipherKey().toByteArray(), "AES"),
                                 new SecretKeySpec(messageKey.getMacKey().toByteArray(), "HmacSHA256"),
                                 messageKey.getIndex());

        messageKeyIterator.remove();
        break;
      }
    }

    Chain updatedChain = chain.toBuilder().clearMessageKeys()
                              .addAllMessageKeys(messageKeyList)
                              .build();

    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setReceiverChains(chainAndIndex.second, updatedChain)
                                                 .build();

    return result;
  }

  public void setMessageKeys(ECPublicKey senderEphemeral, MessageKeys messageKeys) {
    Pair<Chain,Integer> chainAndIndex       = getReceiverChain(senderEphemeral);
    Chain               chain               = chainAndIndex.first;
    Chain.MessageKey    messageKeyStructure = Chain.MessageKey.newBuilder()
                                                              .setCipherKey(ByteString.copyFrom(messageKeys.getCipherKey().getEncoded()))
                                                              .setMacKey(ByteString.copyFrom(messageKeys.getMacKey().getEncoded()))
                                                              .setIndex(messageKeys.getCounter())
                                                              .build();

    Chain updatedChain = chain.toBuilder()
                              .addMessageKeys(messageKeyStructure)
                              .build();

    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setReceiverChains(chainAndIndex.second, updatedChain)
                                                 .build();
  }

  public void setReceiverChainKey(ECPublicKey senderEphemeral, ChainKey chainKey) {
    Pair<Chain,Integer> chainAndIndex = getReceiverChain(senderEphemeral);
    Chain               chain         = chainAndIndex.first;

    Chain.ChainKey chainKeyStructure = Chain.ChainKey.newBuilder()
                                                     .setKey(ByteString.copyFrom(chainKey.getKey()))
                                                     .setIndex(chainKey.getIndex())
                                                     .build();

    Chain updatedChain = chain.toBuilder().setChainKey(chainKeyStructure).build();

    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setReceiverChains(chainAndIndex.second, updatedChain)
                                                 .build();
  }

  public void setPendingKeyExchange(int sequence,
                                    ECKeyPair ourBaseKey,
                                    ECKeyPair ourEphemeralKey,
                                    IdentityKeyPair ourIdentityKey)
  {
    PendingKeyExchange structure =
        PendingKeyExchange.newBuilder()
                          .setSequence(sequence)
                          .setLocalBaseKey(ByteString.copyFrom(ourBaseKey.getPublicKey().serialize()))
                          .setLocalBaseKeyPrivate(ByteString.copyFrom(ourBaseKey.getPrivateKey().serialize()))
                          .setLocalEphemeralKey(ByteString.copyFrom(ourEphemeralKey.getPublicKey().serialize()))
                          .setLocalEphemeralKeyPrivate(ByteString.copyFrom(ourEphemeralKey.getPrivateKey().serialize()))
                          .setLocalIdentityKey(ByteString.copyFrom(ourIdentityKey.getPublicKey().serialize()))
                          .setLocalIdentityKeyPrivate(ByteString.copyFrom(ourIdentityKey.getPrivateKey().serialize()))
                          .build();

    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setPendingKeyExchange(structure)
                                                 .build();
  }

  public int getPendingKeyExchangeSequence() {
    return sessionStructure.getPendingKeyExchange().getSequence();
  }

  public ECKeyPair getPendingKeyExchangeBaseKey() throws InvalidKeyException {
    ECPublicKey publicKey   = Curve.decodePoint(sessionStructure.getPendingKeyExchange()
                                                                .getLocalBaseKey().toByteArray(), 0);

    ECPrivateKey privateKey = Curve.decodePrivatePoint(sessionStructure.getPendingKeyExchange()
                                                                       .getLocalBaseKeyPrivate()
                                                                       .toByteArray());

    return new ECKeyPair(publicKey, privateKey);
  }

  public ECKeyPair getPendingKeyExchangeEphemeralKey() throws InvalidKeyException {
    ECPublicKey publicKey   = Curve.decodePoint(sessionStructure.getPendingKeyExchange()
                                                                .getLocalEphemeralKey().toByteArray(), 0);

    ECPrivateKey privateKey = Curve.decodePrivatePoint(sessionStructure.getPendingKeyExchange()
                                                                       .getLocalEphemeralKeyPrivate()
                                                                       .toByteArray());

    return new ECKeyPair(publicKey, privateKey);
  }

  public IdentityKeyPair getPendingKeyExchangeIdentityKey() throws InvalidKeyException {
    IdentityKey publicKey = new IdentityKey(sessionStructure.getPendingKeyExchange()
                                                            .getLocalIdentityKey().toByteArray(), 0);

    ECPrivateKey privateKey = Curve.decodePrivatePoint(sessionStructure.getPendingKeyExchange()
                                                                       .getLocalIdentityKeyPrivate()
                                                                       .toByteArray());

    return new IdentityKeyPair(publicKey, privateKey);
  }

  public boolean hasPendingKeyExchange() {
    return sessionStructure.hasPendingKeyExchange();
  }

  public void setPendingPreKey(int preKeyId, ECPublicKey baseKey) {
    PendingPreKey pending = PendingPreKey.newBuilder()
                                         .setPreKeyId(preKeyId)
                                         .setBaseKey(ByteString.copyFrom(baseKey.serialize()))
                                         .build();

    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setPendingPreKey(pending)
                                                 .build();
  }

  public boolean hasPendingPreKey() {
    return this.sessionStructure.hasPendingPreKey();
  }

  public Pair<Integer, ECPublicKey> getPendingPreKey() {
    try {
      return new Pair<Integer, ECPublicKey>(sessionStructure.getPendingPreKey().getPreKeyId(),
                                            Curve.decodePoint(sessionStructure.getPendingPreKey()
                                                                              .getBaseKey()
                                                                              .toByteArray(), 0));
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public void clearPendingPreKey() {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .clearPendingPreKey()
                                                 .build();
  }

  public void setRemoteRegistrationId(int registrationId) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setRemoteRegistrationId(registrationId)
                                                 .build();
  }

  public int getRemoteRegistrationId() {
    return this.sessionStructure.getRemoteRegistrationId();
  }

  public void setLocalRegistrationId(int registrationId) {
    this.sessionStructure = this.sessionStructure.toBuilder()
                                                 .setLocalRegistrationId(registrationId)
                                                 .build();
  }

  public int getLocalRegistrationId() {
    return this.sessionStructure.getLocalRegistrationId();
  }

  public byte[] serialize() {
    return sessionStructure.toByteArray();
  }
}
