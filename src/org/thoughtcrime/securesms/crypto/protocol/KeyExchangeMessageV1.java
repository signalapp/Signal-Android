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

public class KeyExchangeMessageV1 extends KeyExchangeMessage {

  private final int         messageVersion;
  private final int         supportedVersion;
  private final PublicKey   publicKey;
  private final String      serialized;
  private       IdentityKey identityKey;

  public KeyExchangeMessageV1(Context context, MasterSecret masterSecret,
                              int messageVersion, LocalKeyRecord record, int highIdBits)
  {
    this.publicKey        = new PublicKey(record.getCurrentKeyPair().getPublicKey());
    this.messageVersion   = messageVersion;
    this.supportedVersion = CiphertextMessage.CURRENT_VERSION;

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

  public KeyExchangeMessageV1(String messageBody) throws InvalidVersionException, InvalidKeyException {
    try {
      byte[] keyBytes       = Base64.decode(messageBody);
      this.messageVersion   = Conversions.highBitsToInt(keyBytes[0]);
      this.supportedVersion = Conversions.lowBitsToInt(keyBytes[0]);
      this.serialized       = messageBody;

      if (messageVersion > 1)
        throw new InvalidVersionException("Legacy key exchange with version: " + messageVersion);

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

  @Override
  public boolean isLegacy() {
    return true;
  }

  @Override
  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public PublicKey getRemoteKey() {
    return publicKey;
  }

  @Override
  public int getMaxVersion() {
    return supportedVersion;
  }

  @Override
  public int getVersion() {
    return messageVersion;
  }

  @Override
  public boolean hasIdentityKey() {
    return identityKey != null;
  }

  public String serialize() {
    return serialized;
  }
}
