package org.whispersystems.libaxolotl.state;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.ratchet.ChainKey;
import org.whispersystems.libaxolotl.ratchet.MessageKeys;
import org.whispersystems.libaxolotl.ratchet.RootKey;
import org.whispersystems.libaxolotl.util.Pair;

public interface SessionState {
  public void setNeedsRefresh(boolean needsRefresh);
  public boolean getNeedsRefresh();
  public void setSessionVersion(int version);
  public int getSessionVersion();
  public void setRemoteIdentityKey(IdentityKey identityKey);
  public void setLocalIdentityKey(IdentityKey identityKey);
  public IdentityKey getRemoteIdentityKey();
  public IdentityKey getLocalIdentityKey();
  public int getPreviousCounter();
  public void setPreviousCounter(int previousCounter);
  public RootKey getRootKey();
  public void setRootKey(RootKey rootKey);
  public ECPublicKey getSenderEphemeral();
  public ECKeyPair getSenderEphemeralPair();
  public boolean hasReceiverChain(ECPublicKey senderEphemeral);
  public boolean hasSenderChain();
  public ChainKey getReceiverChainKey(ECPublicKey senderEphemeral);
  public void addReceiverChain(ECPublicKey senderEphemeral, ChainKey chainKey);
  public void setSenderChain(ECKeyPair senderEphemeralPair, ChainKey chainKey);
  public ChainKey getSenderChainKey();
  public void setSenderChainKey(ChainKey nextChainKey);
  public boolean hasMessageKeys(ECPublicKey senderEphemeral, int counter);
  public MessageKeys removeMessageKeys(ECPublicKey senderEphemeral, int counter);
  public void setMessageKeys(ECPublicKey senderEphemeral, MessageKeys messageKeys);
  public void setReceiverChainKey(ECPublicKey senderEphemeral, ChainKey chainKey);
  public void setPendingKeyExchange(int sequence,
                                    ECKeyPair ourBaseKey,
                                    ECKeyPair ourEphemeralKey,
                                    IdentityKeyPair ourIdentityKey);
  public int getPendingKeyExchangeSequence();
  public ECKeyPair getPendingKeyExchangeBaseKey() throws InvalidKeyException;
  public ECKeyPair getPendingKeyExchangeEphemeralKey() throws InvalidKeyException;
  public IdentityKeyPair getPendingKeyExchangeIdentityKey() throws InvalidKeyException;
  public boolean hasPendingKeyExchange();
  public void setPendingPreKey(int preKeyId, ECPublicKey baseKey);
  public boolean hasPendingPreKey();
  public Pair<Integer, ECPublicKey> getPendingPreKey();
  public void clearPendingPreKey();
  public void setRemoteRegistrationId(int registrationId);
  public int getRemoteRegistrationId();
  public void setLocalRegistrationId(int registrationId);
  public int getLocalRegistrationId();
  public byte[] serialize();
}
