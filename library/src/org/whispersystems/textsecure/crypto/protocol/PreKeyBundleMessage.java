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
package org.whispersystems.textsecure.crypto.protocol;

import org.whispersystems.textsecure.crypto.MessageCipher;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Conversions;

import java.io.IOException;

/**
 * Class responsible for parsing and constructing PreKeyBundle messages.
 *
 * The class takes an existing encrypted message and bundles in the necessary
 * additional information for a prekeybundle, namely the addition of the local
 * identity key.
 */
public class PreKeyBundleMessage {

  private static final int VERSION_LENGTH      = MessageCipher.VERSION_LENGTH;
  private static final int IDENTITY_KEY_LENGTH = IdentityKey.SIZE;
  public  static final int HEADER_LENGTH       = IDENTITY_KEY_LENGTH + MessageCipher.HEADER_LENGTH;

  private static final int VERSION_OFFSET         = MessageCipher.VERSION_OFFSET;
  private static final int IDENTITY_KEY_OFFSET    = VERSION_OFFSET + MessageCipher.VERSION_LENGTH;
  private static final int PUBLIC_KEY_OFFSET      = IDENTITY_KEY_LENGTH + MessageCipher.NEXT_KEY_OFFSET;
  private static final int PREKEY_ID_OFFSET       = IDENTITY_KEY_LENGTH + MessageCipher.RECEIVER_KEY_ID_OFFSET;

  private final byte[] messageBytes;

  private final int         supportedVersion;
  private final int         messageVersion;
  private final int         preKeyId;
  private final IdentityKey identityKey;
  private final PublicKey   publicKey;
  private final byte[]      bundledMessage;

  public PreKeyBundleMessage(String message) throws InvalidKeyException, InvalidVersionException {
    try {
      this.messageBytes   = Base64.decodeWithoutPadding(message);
      this.messageVersion = Conversions.highBitsToInt(this.messageBytes[VERSION_OFFSET]);

      if (messageVersion > MessageCipher.SUPPORTED_VERSION)
        throw new InvalidVersionException("Key exchange with version: " + messageVersion +
                                              " but we only support: " + MessageCipher.SUPPORTED_VERSION);

      this.supportedVersion = Conversions.lowBitsToInt(messageBytes[VERSION_OFFSET]);
      this.publicKey        = new PublicKey(messageBytes, PUBLIC_KEY_OFFSET);
      this.identityKey      = new IdentityKey(messageBytes, IDENTITY_KEY_OFFSET);
      this.preKeyId         = Conversions.byteArrayToMedium(messageBytes, PREKEY_ID_OFFSET);
      this.bundledMessage   = new byte[messageBytes.length - IDENTITY_KEY_LENGTH];


      this.bundledMessage[VERSION_OFFSET] = this.messageBytes[VERSION_OFFSET];
      System.arraycopy(messageBytes, IDENTITY_KEY_OFFSET+IDENTITY_KEY_LENGTH, bundledMessage, VERSION_OFFSET+VERSION_LENGTH, bundledMessage.length-VERSION_LENGTH);
    } catch (IOException e) {
      throw new InvalidKeyException(e);
    }
  }

  public PreKeyBundleMessage(IdentityKey identityKey, byte[] bundledMessage) {
    try {
      this.supportedVersion = MessageCipher.SUPPORTED_VERSION;
      this.messageVersion   = MessageCipher.SUPPORTED_VERSION;
      this.identityKey      = identityKey;
      this.publicKey        = new PublicKey(bundledMessage, MessageCipher.NEXT_KEY_OFFSET);
      this.preKeyId         = Conversions.byteArrayToMedium(bundledMessage, MessageCipher.RECEIVER_KEY_ID_OFFSET);
      this.bundledMessage   = bundledMessage;
      this.messageBytes     = new byte[IDENTITY_KEY_LENGTH + bundledMessage.length];

      byte[] identityKeyBytes = identityKey.serialize();

      messageBytes[VERSION_OFFSET] = bundledMessage[VERSION_OFFSET];
      System.arraycopy(identityKeyBytes, 0, messageBytes, IDENTITY_KEY_OFFSET, identityKeyBytes.length);
      System.arraycopy(bundledMessage, VERSION_OFFSET+VERSION_LENGTH, messageBytes, IDENTITY_KEY_OFFSET+IDENTITY_KEY_LENGTH, bundledMessage.length-VERSION_LENGTH);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public String serialize() {
    return Base64.encodeBytesWithoutPadding(this.messageBytes);
  }

  public int getSupportedVersion() {
    return supportedVersion;
  }

  public int getMessageVersion() {
    return messageVersion;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public String getBundledMessage() {
    return Base64.encodeBytesWithoutPadding(bundledMessage);
  }

  public int getPreKeyId() {
    return preKeyId;
  }
}
