package org.whispersystems.textsecure.crypto.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.LegacyMessageException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

public class PreKeyWhisperMessage implements CiphertextMessage {

  private final int            version;
  private final int            registrationId;
  private final int            preKeyId;
  private final ECPublicKey    baseKey;
  private final IdentityKey    identityKey;
  private final WhisperMessage message;
  private final byte[]         serialized;

  public PreKeyWhisperMessage(byte[] serialized)
      throws InvalidMessageException, InvalidVersionException
  {
    try {
      this.version = Conversions.highBitsToInt(serialized[0]);

      if (this.version > CiphertextMessage.CURRENT_VERSION) {
        throw new InvalidVersionException("Unknown version: " + this.version);
      }

      WhisperProtos.PreKeyWhisperMessage preKeyWhisperMessage
          = WhisperProtos.PreKeyWhisperMessage.parseFrom(ByteString.copyFrom(serialized, 1,
                                                                             serialized.length-1));

      if (!preKeyWhisperMessage.hasPreKeyId()       ||
          !preKeyWhisperMessage.hasBaseKey()        ||
          !preKeyWhisperMessage.hasIdentityKey()    ||
          !preKeyWhisperMessage.hasMessage())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized     = serialized;
      this.registrationId = preKeyWhisperMessage.getRegistrationId();
      this.preKeyId       = preKeyWhisperMessage.getPreKeyId();
      this.baseKey        = Curve.decodePoint(preKeyWhisperMessage.getBaseKey().toByteArray(), 0);
      this.identityKey    = new IdentityKey(Curve.decodePoint(preKeyWhisperMessage.getIdentityKey().toByteArray(), 0));
      this.message        = new WhisperMessage(preKeyWhisperMessage.getMessage().toByteArray());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    } catch (LegacyMessageException e) {
      throw new InvalidMessageException(e);
    }
  }

  public PreKeyWhisperMessage(int registrationId, int preKeyId, ECPublicKey baseKey,
                              IdentityKey identityKey, WhisperMessage message)
  {
    this.version        = CiphertextMessage.CURRENT_VERSION;
    this.registrationId = registrationId;
    this.preKeyId       = preKeyId;
    this.baseKey        = baseKey;
    this.identityKey    = identityKey;
    this.message        = message;

    byte[] versionBytes = {Conversions.intsToByteHighAndLow(CURRENT_VERSION, this.version)};
    byte[] messageBytes = WhisperProtos.PreKeyWhisperMessage.newBuilder()
                                       .setPreKeyId(preKeyId)
                                       .setBaseKey(ByteString.copyFrom(baseKey.serialize()))
                                       .setIdentityKey(ByteString.copyFrom(identityKey.serialize()))
                                       .setMessage(ByteString.copyFrom(message.serialize()))
                                       .setRegistrationId(registrationId)
                                       .build().toByteArray();

    this.serialized = Util.combine(versionBytes, messageBytes);
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public int getPreKeyId() {
    return preKeyId;
  }

  public ECPublicKey getBaseKey() {
    return baseKey;
  }

  public WhisperMessage getWhisperMessage() {
    return message;
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return CiphertextMessage.PREKEY_TYPE;
  }

}
