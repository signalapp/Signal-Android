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

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.util.Conversions;

/**
 * Class responsible for parsing and constructing PreKeyBundle messages.
 *
 * The class takes an existing encrypted message and bundles in the necessary
 * additional information for a prekeybundle, namely the addition of the local
 * identity key.
 */
public class PreKeyBundleMessage {

  public static final int SUPPORTED_VERSION = CiphertextMessage.SUPPORTED_VERSION;

  private static final int VERSION_LENGTH      = CiphertextMessage.VERSION_LENGTH;
  private static final int IDENTITY_KEY_LENGTH = IdentityKey.SIZE;

  private static final int VERSION_OFFSET      = CiphertextMessage.VERSION_OFFSET;
  private static final int IDENTITY_KEY_OFFSET = VERSION_OFFSET + VERSION_LENGTH;

  private final byte[]            messageBytes;
  private final CiphertextMessage bundledMessage;
  private final IdentityKey       identityKey;

  public PreKeyBundleMessage(byte[] messageBytes)
      throws InvalidVersionException, InvalidKeyException
  {
    try {
      this.messageBytes  = messageBytes;
      int messageVersion = Conversions.highBitsToInt(this.messageBytes[VERSION_OFFSET]);

      if (messageVersion > CiphertextMessage.SUPPORTED_VERSION)
        throw new InvalidVersionException("Key exchange with version: " + messageVersion);

      this.identityKey = new IdentityKey(messageBytes, IDENTITY_KEY_OFFSET);
      byte[] bundledMessageBytes = new byte[messageBytes.length - IDENTITY_KEY_LENGTH];

      bundledMessageBytes[VERSION_OFFSET] = this.messageBytes[VERSION_OFFSET];
      System.arraycopy(messageBytes, IDENTITY_KEY_OFFSET+IDENTITY_KEY_LENGTH, bundledMessageBytes,
                       VERSION_OFFSET+VERSION_LENGTH, bundledMessageBytes.length-VERSION_LENGTH);

      this.bundledMessage = new CiphertextMessage(bundledMessageBytes);
    } catch (InvalidMessageException e) {
      throw new InvalidKeyException(e);
    }
  }

  public PreKeyBundleMessage(CiphertextMessage bundledMessage, IdentityKey identityKey) {
    this.bundledMessage = bundledMessage;
    this.identityKey    = identityKey;
    this.messageBytes   = new byte[IDENTITY_KEY_LENGTH + bundledMessage.serialize().length];

    byte[] bundledMessageBytes = bundledMessage.serialize();
    byte[] identityKeyBytes    = identityKey.serialize();

    messageBytes[VERSION_OFFSET] = bundledMessageBytes[VERSION_OFFSET];
    System.arraycopy(identityKeyBytes, 0, messageBytes, IDENTITY_KEY_OFFSET, identityKeyBytes.length);
    System.arraycopy(bundledMessageBytes, VERSION_OFFSET+VERSION_LENGTH, messageBytes, IDENTITY_KEY_OFFSET+IDENTITY_KEY_LENGTH, bundledMessageBytes.length-VERSION_LENGTH);
  }

  public byte[] serialize() {
    return this.messageBytes;
  }

  public int getSupportedVersion() {
    return bundledMessage.getSupportedVersion();
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public PublicKey getPublicKey() throws InvalidKeyException {
    return new PublicKey(bundledMessage.getNextKeyBytes());
  }

  public CiphertextMessage getBundledMessage() {
    return bundledMessage;
  }

  public int getPreKeyId() {
    return bundledMessage.getReceiverKeyId();
  }
}
