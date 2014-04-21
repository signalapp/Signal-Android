package org.whispersystems.test;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.SessionState;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.ratchet.ChainKey;
import org.whispersystems.libaxolotl.ratchet.MessageKeys;
import org.whispersystems.libaxolotl.ratchet.RootKey;
import org.whispersystems.libaxolotl.util.Pair;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

public class InMemorySessionState implements SessionState {

  private Map<ECPublicKey, InMemoryChain> receiverChains = new HashMap<>();

  private boolean     needsRefresh;
  private int         sessionVersion;
  private IdentityKey remoteIdentityKey;
  private IdentityKey localIdentityKey;
  private int         previousCounter;
  private RootKey     rootKey;
  private ECKeyPair   senderEphemeral;
  private ChainKey    senderChainKey;
  private int         pendingPreKeyid;
  private ECPublicKey pendingPreKey;
  private int         remoteRegistrationId;
  private int         localRegistrationId;

  public InMemorySessionState() {}

  public InMemorySessionState(SessionState sessionState) {
    try {
      this.needsRefresh         = sessionState.getNeedsRefresh();
      this.sessionVersion       = sessionState.getSessionVersion();
      this.remoteIdentityKey    = new IdentityKey(sessionState.getRemoteIdentityKey().serialize(), 0);
      this.localIdentityKey     = new IdentityKey(sessionState.getLocalIdentityKey().serialize(), 0);
      this.previousCounter      = sessionState.getPreviousCounter();
      this.rootKey              = new RootKey(sessionState.getRootKey().getKeyBytes());
      this.senderEphemeral      = sessionState.getSenderEphemeralPair();
      this.senderChainKey       = new ChainKey(sessionState.getSenderChainKey().getKey(),
                                               sessionState.getSenderChainKey().getIndex());
      this.pendingPreKeyid      = sessionState.getPendingPreKey().first();
      this.pendingPreKey        = sessionState.getPendingPreKey().second();
      this.remoteRegistrationId = sessionState.getRemoteRegistrationId();
      this.localRegistrationId  = sessionState.getLocalRegistrationId();
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void setNeedsRefresh(boolean needsRefresh) {
    this.needsRefresh = needsRefresh;
  }

  @Override
  public boolean getNeedsRefresh() {
    return needsRefresh;
  }

  @Override
  public void setSessionVersion(int version) {
    this.sessionVersion = version;
  }

  @Override
  public int getSessionVersion() {
    return sessionVersion;
  }

  @Override
  public void setRemoteIdentityKey(IdentityKey identityKey) {
    this.remoteIdentityKey = identityKey;
  }

  @Override
  public void setLocalIdentityKey(IdentityKey identityKey) {
    this.localIdentityKey = identityKey;

  }

  @Override
  public IdentityKey getRemoteIdentityKey() {
    return remoteIdentityKey;
  }

  @Override
  public IdentityKey getLocalIdentityKey() {
    return localIdentityKey;
  }

  @Override
  public int getPreviousCounter() {
    return previousCounter;
  }

  @Override
  public void setPreviousCounter(int previousCounter) {
    this.previousCounter = previousCounter;
  }

  @Override
  public RootKey getRootKey() {
    return rootKey;
  }

  @Override
  public void setRootKey(RootKey rootKey) {
    this.rootKey = rootKey;
  }

  @Override
  public ECPublicKey getSenderEphemeral() {
    return senderEphemeral.getPublicKey();
  }

  @Override
  public ECKeyPair getSenderEphemeralPair() {
    return senderEphemeral;
  }

  @Override
  public boolean hasReceiverChain(ECPublicKey senderEphemeral) {
    return receiverChains.containsKey(senderEphemeral);
  }

  @Override
  public boolean hasSenderChain() {
    return senderChainKey != null;
  }

  @Override
  public ChainKey getReceiverChainKey(ECPublicKey senderEphemeral) {
    InMemoryChain chain = receiverChains.get(senderEphemeral);
    return new ChainKey(chain.chainKey, chain.index);
  }

  @Override
  public void addReceiverChain(ECPublicKey senderEphemeral, ChainKey chainKey) {
    InMemoryChain chain = new InMemoryChain();
    chain.chainKey = chainKey.getKey();
    chain.index    = chainKey.getIndex();

    receiverChains.put(senderEphemeral, chain);
  }

  @Override
  public void setSenderChain(ECKeyPair senderEphemeralPair, ChainKey chainKey) {
    this.senderEphemeral = senderEphemeralPair;
    this.senderChainKey  = chainKey;
  }

  @Override
  public ChainKey getSenderChainKey() {
    return senderChainKey;
  }

  @Override
  public void setSenderChainKey(ChainKey nextChainKey) {
    this.senderChainKey = nextChainKey;
  }

  @Override
  public boolean hasMessageKeys(ECPublicKey senderEphemeral, int counter) {
    InMemoryChain chain = receiverChains.get(senderEphemeral);

    if (chain == null) return false;

    for (InMemoryChain.InMemoryMessageKey messageKey : chain.messageKeys) {
      if (messageKey.index == counter) {
        return true;
      }
    }

    return false;
  }

  @Override
  public MessageKeys removeMessageKeys(ECPublicKey senderEphemeral, int counter) {
    InMemoryChain chain   = receiverChains.get(senderEphemeral);
    MessageKeys   results = null;

    if (chain == null) return null;

    Iterator<InMemoryChain.InMemoryMessageKey> iterator = chain.messageKeys.iterator();

    while (iterator.hasNext()) {
      InMemoryChain.InMemoryMessageKey messageKey = iterator.next();

      if (messageKey.index == counter) {
        results = new MessageKeys(new SecretKeySpec(messageKey.cipherKey, "AES"),
                                  new SecretKeySpec(messageKey.macKey, "HmacSHA256"),
                                  messageKey.index);

        iterator.remove();
        break;
      }
    }

    return results;
  }

  @Override
  public void setMessageKeys(ECPublicKey senderEphemeral, MessageKeys messageKeys) {
    InMemoryChain                    chain = receiverChains.get(senderEphemeral);
    InMemoryChain.InMemoryMessageKey key   = new InMemoryChain.InMemoryMessageKey();

    key.cipherKey = messageKeys.getCipherKey().getEncoded();
    key.macKey    = messageKeys.getMacKey().getEncoded();
    key.index     = messageKeys.getCounter();

    chain.messageKeys.add(key);
  }

  @Override
  public void setReceiverChainKey(ECPublicKey senderEphemeral, ChainKey chainKey) {
    InMemoryChain chain = receiverChains.get(senderEphemeral);
    chain.chainKey = chainKey.getKey();
    chain.index    = chainKey.getIndex();
  }

  @Override
  public void setPendingKeyExchange(int sequence, ECKeyPair ourBaseKey, ECKeyPair ourEphemeralKey, IdentityKeyPair ourIdentityKey) {
    throw new AssertionError();
  }

  @Override
  public int getPendingKeyExchangeSequence() {
    throw new AssertionError();
  }

  @Override
  public ECKeyPair getPendingKeyExchangeBaseKey() throws InvalidKeyException {
    throw new AssertionError();
  }

  @Override
  public ECKeyPair getPendingKeyExchangeEphemeralKey() throws InvalidKeyException {
    throw new AssertionError();
  }

  @Override
  public IdentityKeyPair getPendingKeyExchangeIdentityKey() throws InvalidKeyException {
    throw new AssertionError();
  }

  @Override
  public boolean hasPendingKeyExchange() {
    throw new AssertionError();
  }

  @Override
  public void setPendingPreKey(int preKeyId, ECPublicKey baseKey) {
    this.pendingPreKeyid = preKeyId;
    this.pendingPreKey   = baseKey;
  }

  @Override
  public boolean hasPendingPreKey() {
    return this.pendingPreKey != null;
  }

  @Override
  public Pair<Integer, ECPublicKey> getPendingPreKey() {
    return new Pair<>(pendingPreKeyid, pendingPreKey);
  }

  @Override
  public void clearPendingPreKey() {
    this.pendingPreKey = null;
    this.pendingPreKeyid = -1;
  }

  @Override
  public void setRemoteRegistrationId(int registrationId) {
    this.remoteRegistrationId = registrationId;
  }

  @Override
  public int getRemoteRegistrationId() {
    return remoteRegistrationId;
  }

  @Override
  public void setLocalRegistrationId(int registrationId) {
    this.localRegistrationId = registrationId;
  }

  @Override
  public int getLocalRegistrationId() {
    return localRegistrationId;
  }

  @Override
  public byte[] serialize() {
    throw new AssertionError();
  }

  private static class InMemoryChain {
    byte[] chainKey;
    int    index;
    List<InMemoryMessageKey> messageKeys = new LinkedList<>();

    public static class InMemoryMessageKey {
      public InMemoryMessageKey(){}
      int    index;
      byte[] cipherKey;
      byte[] macKey;
    }
  }
}
