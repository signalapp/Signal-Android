/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

/**
 * A disk record representing a current session.
 *
 * @author Moxie Marlinspike
 */

public class SessionRecordV2 extends Record {

  private static final Object FILE_LOCK = new Object();
  private static final int CURRENT_VERSION = 1;

  private final MasterSecret masterSecret;
  private StorageProtos.SessionStructure sessionStructure =
      StorageProtos.SessionStructure.newBuilder().build();

  public SessionRecordV2(Context context, MasterSecret masterSecret, CanonicalRecipientAddress recipient) {
    this(context, masterSecret, getRecipientId(context, recipient));
  }

  public SessionRecordV2(Context context, MasterSecret masterSecret, long recipientId) {
    super(context, SESSIONS_DIRECTORY_V2, recipientId+"");
    this.masterSecret = masterSecret;
    loadData();
  }

  public static void delete(Context context, CanonicalRecipientAddress recipient) {
    delete(context, SESSIONS_DIRECTORY_V2, getRecipientId(context, recipient) + "");
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret,
                                   CanonicalRecipientAddress recipient)
  {
    return hasSession(context, masterSecret, getRecipientId(context, recipient));
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, long recipientId) {
    return hasRecord(context, SESSIONS_DIRECTORY_V2, recipientId+"") &&
        new SessionRecordV2(context, masterSecret, recipientId).hasSenderChain();
  }

  private static long getRecipientId(Context context, CanonicalRecipientAddress recipient) {
    return recipient.getCanonicalAddress(context);
  }

  public void clear() {
    this.sessionStructure = StorageProtos.SessionStructure.newBuilder().build();
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
    ECPublicKey publicKey = getSenderEphemeral();
    ECPrivateKey privateKey = Curve.decodePrivatePoint(publicKey.getType(),
                                                       sessionStructure.getSenderChain()
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

    List<Chain.MessageKey>     messageKeyList     = chain.getMessageKeysList();
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

    ECPrivateKey privateKey = Curve.decodePrivatePoint(publicKey.getType(),
                                                       sessionStructure.getPendingKeyExchange()
                                                                       .getLocalBaseKeyPrivate()
                                                                       .toByteArray());

    return new ECKeyPair(publicKey, privateKey);
  }

  public ECKeyPair getPendingKeyExchangeEphemeralKey() throws InvalidKeyException {
    ECPublicKey publicKey   = Curve.decodePoint(sessionStructure.getPendingKeyExchange()
                                                                .getLocalEphemeralKey().toByteArray(), 0);

    ECPrivateKey privateKey = Curve.decodePrivatePoint(publicKey.getType(),
                                                       sessionStructure.getPendingKeyExchange()
                                                                       .getLocalEphemeralKeyPrivate()
                                                                       .toByteArray());

    return new ECKeyPair(publicKey, privateKey);
  }

  public IdentityKeyPair getPendingKeyExchangeIdentityKey() throws InvalidKeyException {
    IdentityKey publicKey = new IdentityKey(sessionStructure.getPendingKeyExchange()
                                                            .getLocalIdentityKey().toByteArray(), 0);

    ECPrivateKey privateKey = Curve.decodePrivatePoint(publicKey.getPublicKey().getType(),
                                                       sessionStructure.getPendingKeyExchange()
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

  public void save() {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);

        MasterCipher cipher = new MasterCipher(masterSecret);
        writeInteger(CURRENT_VERSION, out);
        writeBlob(cipher.encryptBytes(sessionStructure.toByteArray()), out);

        out.truncate(out.position());
        file.close();
      } catch (IOException ioe) {
        throw new IllegalArgumentException(ioe);
      }
    }
  }

  private void loadData() {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in = this.openInputStream();
        int versionMarker  = readInteger(in);

        if (versionMarker > CURRENT_VERSION) {
          throw new AssertionError("Unknown version: " + versionMarker);
        }

        MasterCipher cipher = new MasterCipher(masterSecret);
        byte[] encryptedBlob = readBlob(in);


        this.sessionStructure = StorageProtos.SessionStructure
                                             .parseFrom(cipher.decryptBytes(encryptedBlob));

        in.close();
      } catch (FileNotFoundException e) {
        Log.w("SessionRecordV2", "No session information found.");
        // XXX
      } catch (IOException ioe) {
        Log.w("SessionRecordV2", ioe);
        // XXX
      } catch (InvalidMessageException e) {
        Log.w("SessionRecordV2", e);
      }
    }
  }

}
