/**
 * Copyright (C) 2011 Whisper Systems
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
package org.whispersystems.textsecure.crypto.protocol;

import android.content.Context;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.crypto.SessionCipher.SessionCipherContext;
import org.whispersystems.textsecure.crypto.TransportDetails;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;
import org.whispersystems.textsecure.util.Conversions;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Parses and serializes the encrypted message format.
 *
 * @author Moxie Marlinspike
 */

public class EncryptedMessage {

  public static final int SUPPORTED_VERSION       = 1;

  private static final int VERSION_LENGTH         = 1;
  private static final int SENDER_KEY_ID_LENGTH   = 3;
  private static final int RECEIVER_KEY_ID_LENGTH = 3;
  private static final int NEXT_KEY_LENGTH        = PublicKey.KEY_SIZE;
  private static final int COUNTER_LENGTH         = 3;
  public static final int HEADER_LENGTH          =  VERSION_LENGTH + SENDER_KEY_ID_LENGTH + RECEIVER_KEY_ID_LENGTH + COUNTER_LENGTH + NEXT_KEY_LENGTH;

  private static final int VERSION_OFFSET         = 0;
  private static final int SENDER_KEY_ID_OFFSET   = VERSION_OFFSET + VERSION_LENGTH;
  private static final int RECEIVER_KEY_ID_OFFSET = SENDER_KEY_ID_OFFSET + SENDER_KEY_ID_LENGTH;
  private static final int NEXT_KEY_OFFSET        = RECEIVER_KEY_ID_OFFSET + RECEIVER_KEY_ID_LENGTH;
  private static final int COUNTER_OFFSET         = NEXT_KEY_OFFSET + NEXT_KEY_LENGTH;
  private static final int TEXT_OFFSET            = COUNTER_OFFSET + COUNTER_LENGTH;

  private final Context          context;
  private final MasterSecret     masterSecret;
  private final TransportDetails transportDetails;

  public EncryptedMessage(Context context, MasterSecret masterSecret, TransportDetails transportDetails) {
    this.context          = context.getApplicationContext();
    this.masterSecret     = masterSecret;
    this.transportDetails = transportDetails;
  }

  public byte[] encrypt(CanonicalRecipientAddress recipient, byte[] plaintext) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      byte[]               paddedBody          = transportDetails.getPaddedMessageBody(plaintext);
      SessionCipher        sessionCipher       = new SessionCipher();
      SessionCipherContext sessionContext      = sessionCipher.getEncryptionContext(context, masterSecret, recipient);
      byte[]               ciphertextBody      = sessionCipher.encrypt(sessionContext, paddedBody);
      byte[]               formattedCiphertext = getFormattedCiphertext(sessionContext, ciphertextBody);
      byte[]               ciphertextMessage   = sessionCipher.mac(sessionContext, formattedCiphertext);

      return transportDetails.getEncodedMessage(ciphertextMessage);
    }
  }

  public byte[] decrypt(CanonicalRecipientAddress recipient, byte[] ciphertext)
      throws InvalidMessageException
  {
    synchronized (SessionCipher.CIPHER_LOCK) {
      try {
        byte[] decodedMessage = transportDetails.getDecodedMessage(ciphertext);
        int    messageVersion = getMessageVersion(decodedMessage);

        if (messageVersion > SUPPORTED_VERSION) {
          throw new InvalidMessageException("Unsupported version: " + messageVersion);
        }

        int                  supportedVersion = getSupportedVersion(decodedMessage);
        int                  receiverKeyId    = getReceiverKeyId(decodedMessage);
        int                  senderKeyId      = getSenderKeyId(decodedMessage);
        int                  counter          = getCiphertextCounter(decodedMessage);
        byte[]               ciphertextBody   = getMessageBody(decodedMessage);
        PublicKey            nextRemoteKey    = getNextRemoteKey(decodedMessage);
        int                  version          = Math.min(supportedVersion, SUPPORTED_VERSION);
        SessionCipher        sessionCipher    = new SessionCipher();
        SessionCipherContext sessionContext   = sessionCipher.getDecryptionContext(context, masterSecret,
                                                                                   recipient, senderKeyId,
                                                                                   receiverKeyId,
                                                                                   nextRemoteKey,
                                                                                   counter, version);

        sessionCipher.verifyMac(sessionContext, decodedMessage);

        byte[] plaintextWithPadding = sessionCipher.decrypt(sessionContext, ciphertextBody);
        return transportDetails.getStrippedPaddingMessageBody(plaintextWithPadding);
      } catch (IOException e) {
        throw new InvalidMessageException(e);
      } catch (InvalidKeyException e) {
        throw new InvalidMessageException(e);
      }
    }
  }

  private byte[] getFormattedCiphertext(SessionCipherContext sessionContext, byte[] ciphertextBody) {
    ByteBuffer buffer             = ByteBuffer.allocate(HEADER_LENGTH + ciphertextBody.length);
    byte       versionByte        = Conversions.intsToByteHighAndLow(sessionContext.getNegotiatedVersion(), SUPPORTED_VERSION);
    byte[]     senderKeyIdBytes   = Conversions.mediumToByteArray(sessionContext.getSenderKeyId());
    byte[]     receiverKeyIdBytes = Conversions.mediumToByteArray(sessionContext.getRecipientKeyId());
    byte[]     nextKeyBytes       = sessionContext.getNextKey().serialize();
    byte[]     counterBytes       = Conversions.mediumToByteArray(sessionContext.getCounter());

    buffer.put(versionByte);
    buffer.put(senderKeyIdBytes);
    buffer.put(receiverKeyIdBytes);
    buffer.put(nextKeyBytes);
    buffer.put(counterBytes);
    buffer.put(ciphertextBody);

    return buffer.array();
  }

  private int getMessageVersion(byte[] message) {
    return Conversions.highBitsToInt(message[VERSION_OFFSET]);
  }

  private int getSupportedVersion(byte[] message) {
    return Conversions.lowBitsToInt(message[VERSION_OFFSET]);
  }

  private int getSenderKeyId(byte[] message) {
    return Conversions.byteArrayToMedium(message, SENDER_KEY_ID_OFFSET);
  }

  private int getReceiverKeyId(byte[] message) {
    return Conversions.byteArrayToMedium(message, RECEIVER_KEY_ID_OFFSET);
  }

  private int getCiphertextCounter(byte[] message) {
    return Conversions.byteArrayToMedium(message, COUNTER_OFFSET);
  }

  private byte[] getMessageBody(byte[] message) {
    byte[] body = new byte[message.length - HEADER_LENGTH];
    System.arraycopy(message, TEXT_OFFSET, body, 0, body.length);

    return body;
  }

  private PublicKey getNextRemoteKey(byte[] message) throws InvalidKeyException {
    byte[] key = new byte[NEXT_KEY_LENGTH];
    System.arraycopy(message, NEXT_KEY_OFFSET, key, 0, key.length);

    return new PublicKey(key);
  }
}
