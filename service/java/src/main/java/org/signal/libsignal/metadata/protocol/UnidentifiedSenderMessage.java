package org.signal.libsignal.metadata.protocol;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.SignalProtos;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.ByteUtil;

public class UnidentifiedSenderMessage {

  private static final int CIPHERTEXT_VERSION = 1;

  private final int         version;
  private final ECPublicKey ephemeral;
  private final byte[]      encryptedStatic;
  private final byte[]      encryptedMessage;
  private final byte[]      serialized;

  public UnidentifiedSenderMessage(byte[] serialized)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException
  {
    try {
      this.version = ByteUtil.highBitsToInt(serialized[0]);

      if (version > CIPHERTEXT_VERSION) {
        throw new InvalidMetadataVersionException("Unknown version: " + this.version);
      }

      SignalProtos.UnidentifiedSenderMessage unidentifiedSenderMessage = SignalProtos.UnidentifiedSenderMessage.parseFrom(ByteString.copyFrom(serialized, 1, serialized.length - 1));

      if (!unidentifiedSenderMessage.hasEphemeralPublic() ||
          !unidentifiedSenderMessage.hasEncryptedStatic() ||
          !unidentifiedSenderMessage.hasEncryptedMessage())
      {
        throw new InvalidMetadataMessageException("Missing fields");
      }

      this.ephemeral        = Curve.decodePoint(unidentifiedSenderMessage.getEphemeralPublic().toByteArray(), 0);
      this.encryptedStatic  = unidentifiedSenderMessage.getEncryptedStatic().toByteArray();
      this.encryptedMessage = unidentifiedSenderMessage.getEncryptedMessage().toByteArray();
      this.serialized       = serialized;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  public UnidentifiedSenderMessage(ECPublicKey ephemeral, byte[] encryptedStatic, byte[] encryptedMessage) {
    this.version          = CIPHERTEXT_VERSION;
    this.ephemeral        = ephemeral;
    this.encryptedStatic  = encryptedStatic;
    this.encryptedMessage = encryptedMessage;

    byte[] versionBytes = {ByteUtil.intsToByteHighAndLow(CIPHERTEXT_VERSION, CIPHERTEXT_VERSION)};
    byte[] messageBytes = SignalProtos.UnidentifiedSenderMessage.newBuilder()
                                                                .setEncryptedMessage(ByteString.copyFrom(encryptedMessage))
                                                                .setEncryptedStatic(ByteString.copyFrom(encryptedStatic))
                                                                .setEphemeralPublic(ByteString.copyFrom(ephemeral.serialize()))
                                                                .build()
                                                                .toByteArray();

    this.serialized = ByteUtil.combine(versionBytes, messageBytes);
  }

  public ECPublicKey getEphemeral() {
    return ephemeral;
  }

  public byte[] getEncryptedStatic() {
    return encryptedStatic;
  }

  public byte[] getEncryptedMessage() {
    return encryptedMessage;
  }

  public byte[] getSerialized() {
    return serialized;
  }
}
