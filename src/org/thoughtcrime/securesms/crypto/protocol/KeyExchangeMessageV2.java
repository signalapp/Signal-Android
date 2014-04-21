package org.thoughtcrime.securesms.crypto.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.WhisperProtos;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;

public class KeyExchangeMessageV2 extends KeyExchangeMessage {

  public static final int INITIATE_FLAG              = 0x01;
  public static final int RESPONSE_FLAG              = 0X02;
  public static final int SIMULTAENOUS_INITIATE_FLAG = 0x04;

  private final int         version;
  private final int         supportedVersion;
  private final int         sequence;
  private final int         flags;

  private final ECPublicKey baseKey;
  private final ECPublicKey ephemeralKey;
  private final IdentityKey identityKey;
  private final byte[]      serialized;

  public KeyExchangeMessageV2(int sequence, int flags,
                              ECPublicKey baseKey, ECPublicKey ephemeralKey,
                              IdentityKey identityKey)
  {
    this.supportedVersion = CiphertextMessage.CURRENT_VERSION;
    this.version          = CiphertextMessage.CURRENT_VERSION;
    this.sequence         = sequence;
    this.flags            = flags;
    this.baseKey          = baseKey;
    this.ephemeralKey     = ephemeralKey;
    this.identityKey      = identityKey;

    byte[] version = {Conversions.intsToByteHighAndLow(this.version, this.supportedVersion)};
    byte[] message = WhisperProtos.KeyExchangeMessage.newBuilder()
                                  .setId((sequence << 5) | flags)
                                  .setBaseKey(ByteString.copyFrom(baseKey.serialize()))
                                  .setEphemeralKey(ByteString.copyFrom(ephemeralKey.serialize()))
                                  .setIdentityKey(ByteString.copyFrom(identityKey.serialize()))
                                  .build().toByteArray();

    this.serialized = Util.combine(version, message);
  }

  public KeyExchangeMessageV2(String serializedAndEncoded)
      throws InvalidMessageException, InvalidVersionException, LegacyMessageException
  {
    try {
      byte[]   serialized = Base64.decodeWithoutPadding(serializedAndEncoded);
      byte[][] parts      = Util.split(serialized, 1, serialized.length - 1);

      this.version          = Conversions.highBitsToInt(parts[0][0]);
      this.supportedVersion = Conversions.lowBitsToInt(parts[0][0]);

      if (this.version <= CiphertextMessage.UNSUPPORTED_VERSION) {
        throw new LegacyMessageException("Unsupported legacy version: " + this.version);
      }

      if (this.version > CiphertextMessage.CURRENT_VERSION) {
        throw new InvalidVersionException("Unknown version: " + this.version);
      }

      WhisperProtos.KeyExchangeMessage message = WhisperProtos.KeyExchangeMessage.parseFrom(parts[1]);

      if (!message.hasId() || !message.hasBaseKey() ||
          !message.hasEphemeralKey() || !message.hasIdentityKey())
      {
        throw new InvalidMessageException("Some required fields missing!");
      }

      this.sequence     = message.getId() >> 5;
      this.flags        = message.getId() & 0x1f;
      this.serialized   = serialized;
      this.baseKey      = Curve.decodePoint(message.getBaseKey().toByteArray(), 0);
      this.ephemeralKey = Curve.decodePoint(message.getEphemeralKey().toByteArray(), 0);
      this.identityKey  = new IdentityKey(message.getIdentityKey().toByteArray(), 0);
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ECPublicKey getBaseKey() {
    return baseKey;
  }

  public ECPublicKey getEphemeralKey() {
    return ephemeralKey;
  }

  @Override
  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  @Override
  public boolean hasIdentityKey() {
    return true;
  }

  @Override
  public int getMaxVersion() {
    return supportedVersion;
  }

  public boolean isResponse() {
    return ((flags & RESPONSE_FLAG) != 0);
  }

  public boolean isInitiate() {
    return (flags & INITIATE_FLAG) != 0;
  }

  public boolean isResponseForSimultaneousInitiate() {
    return (flags & SIMULTAENOUS_INITIATE_FLAG) != 0;
  }

  public int getFlags() {
    return flags;
  }

  public int getSequence() {
    return sequence;
  }

  public String serialize() {
    return Base64.encodeBytesWithoutPadding(serialized);
  }
}
