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
package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.database.keys.LocalKeyRecord;
import org.thoughtcrime.securesms.protocol.Message;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.IOException;

public class AbortSessionMessage {
  private final int         messageVersion;
  private final int         supportedVersion;
  private final PublicKey   publicKey;
  private final String      serialized;
  private       IdentityKey identityKey;

  public AbortSessionMessage(Context context, MasterSecret masterSecret, int messageVersion, LocalKeyRecord record, int highIdBits) {
    this.publicKey        = record.getCurrentKeyPair().getPublicKey();
    this.messageVersion   = messageVersion;
    this.supportedVersion = Message.SUPPORTED_VERSION;

    publicKey.setId(publicKey.getId() | (highIdBits << 12));

    byte[] publicKeyBytes   = publicKey.serialize();
    byte[] keyExchangeBytes = new byte[1 + publicKeyBytes.length];

    keyExchangeBytes[0]     = Conversions.intsToByteHighAndLow(messageVersion, supportedVersion);
    System.arraycopy(publicKeyBytes, 0, keyExchangeBytes, 1, publicKeyBytes.length);

    if (includeIdentitySignature(messageVersion, context))
      keyExchangeBytes = IdentityKeyUtil.getSignedKeyExchange(context, masterSecret, keyExchangeBytes);

    if (messageVersion < 1)
      this.serialized = Base64.encodeBytes(keyExchangeBytes);
    else
      this.serialized = Base64.encodeBytesWithoutPadding(keyExchangeBytes);
  }

  public AbortSessionMessage(String messageBody) throws InvalidVersionException, InvalidKeyException {
    try {
      byte[] keyBytes       = Base64.decode(messageBody);
      this.messageVersion   = Conversions.highBitsToInt(keyBytes[0]);
      this.supportedVersion = Conversions.lowBitsToInt(keyBytes[0]);
      this.serialized       = messageBody;

      if (messageVersion >= 1)
        keyBytes = Base64.decodeWithoutPadding(messageBody);

      this.publicKey  = new PublicKey(keyBytes, 1);

      if (keyBytes.length <= PublicKey.KEY_SIZE + 1) {
        this.identityKey = null;
      } else {
        try {
          this.identityKey = IdentityKeyUtil.verifySignedKeyExchange(keyBytes);
        } catch (InvalidKeyException ike) {
          Log.w("AbortSessionMessage", ike);
          this.identityKey = null;
        }
      }
    } catch (IOException ioe) {
      throw new InvalidKeyException(ioe);
    }
  }

  private static boolean includeIdentitySignature(int messageVersion, Context context) {
    return IdentityKeyUtil.hasIdentityKey(context) && (messageVersion >= 1);
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public int getMaxVersion() {
    return supportedVersion;
  }

  public boolean hasIdentityKey() {
    return identityKey != null;
  }

  public String serialize() {
    return serialized;
  }
}
