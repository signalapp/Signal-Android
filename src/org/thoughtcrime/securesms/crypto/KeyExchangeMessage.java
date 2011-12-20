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

import java.io.IOException;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.database.LocalKeyRecord;
import org.thoughtcrime.securesms.protocol.Message;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

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
    this.supportedVersion = Message.SUPPORTED_VERSION;
		
    publicKey.setId(publicKey.getId() | (highIdBits << 12));
		
    byte[] publicKeyBytes   = publicKey.serialize();
    byte[] keyExchangeBytes = new byte[1 + publicKeyBytes.length];
		
    keyExchangeBytes[0]     = Conversions.intsToByteHighAndLow(messageVersion, supportedVersion);
    System.arraycopy(publicKeyBytes, 0, keyExchangeBytes, 1, publicKeyBytes.length);

    if (includeIdentitySignature(messageVersion, context)) 
      keyExchangeBytes = IdentityKeyUtil.getSignedKeyExchange(context, masterSecret, keyExchangeBytes);

    if (messageVersion < 1)
      this.serialized = Prefix.KEY_EXCHANGE + Base64.encodeBytes(keyExchangeBytes);
    else
      this.serialized = Prefix.KEY_EXCHANGE + Base64.encodeBytesWithoutPadding(keyExchangeBytes);
  }
	
  public KeyExchangeMessage(String messageBody) throws InvalidVersionException, InvalidKeyException {
    try {
      String keyString      = messageBody.substring(Prefix.KEY_EXCHANGE.length()); 
      byte[] keyBytes       = Base64.decode(keyString);
      this.messageVersion   = Conversions.highBitsToInt(keyBytes[0]);
      this.supportedVersion = Conversions.lowBitsToInt(keyBytes[0]);
      this.serialized       = messageBody;
			
      if (messageVersion > Message.SUPPORTED_VERSION)
        throw new InvalidVersionException("Key exchange with version: " + messageVersion + " but we only support: " + Message.SUPPORTED_VERSION);
				
      if (messageVersion >= 1)
        keyBytes = Base64.decodeWithoutPadding(keyString);
			
      this.publicKey  = new PublicKey(keyBytes, 1);
			
      if (keyBytes.length <= PublicKey.KEY_SIZE + 1) {
        this.identityKey = null;
      } else {
        try {
          this.identityKey = IdentityKeyUtil.verifySignedKeyExchange(keyBytes);
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
    return IdentityKeyUtil.hasIdentityKey(context) && 
      (messageVersion >= 1)                   &&
      PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.SEND_IDENTITY_PREF, true);
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
