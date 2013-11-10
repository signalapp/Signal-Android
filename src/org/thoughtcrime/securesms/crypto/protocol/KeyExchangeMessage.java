/** 
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto.protocol;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;

/**
 * A class for constructing and parsing key exchange messages.
 * 
 * A key exchange message is basically represented by the following format:
 * 
 * 1) 4 bits <protocol version number>
 * 2) 4 bits <max supported protocol version number>
 * 3) A serialized public key
 * 4) (Optional) An identity key.
 * 5) (if #4)    A signature over the identity key, version bits, and serialized public key.
 * 
 * A serialized public key is basically represented by the following format:
 * 
 * 1) A 3 byte key ID.
 * 2) An ECC key encoded with point compression.
 * 
 * An initiating key ID is initialized with the bottom 12 bits of the ID set.  A responding key
 * ID does the same, but puts the initiating key ID's bottom 12 bits in the top 12 bits of its
 * ID.  This is used to correlate key exchange responses.
 * 
 * @author Moxie Marlinspike
 *
 */

public class KeyExchangeMessage {

  private final int         messageVersion;
  private final int         supportedVersion;
  private final PublicKey   publicKey;
  private final String      serialized;
  private       IdentityKey identityKey;
	
  public KeyExchangeMessage(Context context, MasterSecret masterSecret, int messageVersion, LocalKeyRecord record, int highIdBits) {
    this.publicKey        = new PublicKey(record.getCurrentKeyPair().getPublicKey());
    this.messageVersion   = messageVersion;
    this.supportedVersion = CiphertextMessage.SUPPORTED_VERSION;
		
    publicKey.setId(publicKey.getId() | (highIdBits << 12));

    byte[] versionBytes     = {Conversions.intsToByteHighAndLow(messageVersion, supportedVersion)};
    byte[] publicKeyBytes   = publicKey.serialize();

    byte[] serializedBytes;

    if (includeIdentityNoSignature(messageVersion, context)) {
      byte[] identityKey = IdentityKeyUtil.getIdentityKey(context, Curve.DJB_TYPE).serialize();

      serializedBytes = Util.combine(versionBytes, publicKeyBytes, identityKey);
    } else if (includeIdentitySignature(messageVersion, context)) {
      byte[] prolog = Util.combine(versionBytes, publicKeyBytes);

      serializedBytes = IdentityKeyUtil.getSignedKeyExchange(context, masterSecret, prolog);
    } else {
      serializedBytes = Util.combine(versionBytes, publicKeyBytes);
    }

    if (messageVersion < 1) this.serialized = Base64.encodeBytes(serializedBytes);
    else                    this.serialized = Base64.encodeBytesWithoutPadding(serializedBytes);
  }
	
  public KeyExchangeMessage(String messageBody) throws InvalidVersionException, InvalidKeyException {
    try {
      byte[] keyBytes       = Base64.decode(messageBody);
      this.messageVersion   = Conversions.highBitsToInt(keyBytes[0]);
      this.supportedVersion = Conversions.lowBitsToInt(keyBytes[0]);
      this.serialized       = messageBody;
			
      if (messageVersion > CiphertextMessage.SUPPORTED_VERSION)
        throw new InvalidVersionException("Key exchange with version: " + messageVersion);

      if (messageVersion >= 1)
        keyBytes = Base64.decodeWithoutPadding(messageBody);
			
      this.publicKey = new PublicKey(keyBytes, 1);
			
      if (keyBytes.length <= PublicKey.KEY_SIZE + 1) {
        this.identityKey = null;
      } else if (messageVersion == 1) {
        try {
          this.identityKey = IdentityKeyUtil.verifySignedKeyExchange(keyBytes);
        } catch (InvalidKeyException ike) {
          Log.w("KeyUtil", ike);
          this.identityKey = null;
        }
      } else if (messageVersion == 2) {
        try {
          this.identityKey = new IdentityKey(keyBytes, 1 + PublicKey.KEY_SIZE);
        } catch (InvalidKeyException ike) {
          Log.w("KeyUtil", ike);
          this.identityKey = null;
        }
      }
    } catch (IOException ioe) {
      throw new InvalidKeyException(ioe);
    }
  }
	
  private static boolean includeIdentitySignature(int messageVersion, Context context) {
    return IdentityKeyUtil.hasIdentityKey(context, Curve.NIST_TYPE) && (messageVersion == 1);
  }

  private static boolean includeIdentityNoSignature(int messageVersion, Context context) {
    return IdentityKeyUtil.hasIdentityKey(context, Curve.DJB_TYPE) && (messageVersion >= 2);
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

  public int getMessageVersion() {
    return messageVersion;
  }
	
  public boolean hasIdentityKey() {
    return identityKey != null;
  }
	
  public String serialize() {
    return serialized;
  }
}
