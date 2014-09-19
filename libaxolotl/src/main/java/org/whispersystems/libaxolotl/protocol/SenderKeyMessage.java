package org.whispersystems.libaxolotl.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.ByteUtil;

import java.text.ParseException;

public class SenderKeyMessage implements CiphertextMessage {

  private static final int SIGNATURE_LENGTH = 64;

  private final int         messageVersion;
  private final int         keyId;
  private final int         iteration;
  private final byte[]      ciphertext;
  private final byte[]      serialized;

  public SenderKeyMessage(byte[] serialized) throws InvalidMessageException, LegacyMessageException {
    try {
      byte[][] messageParts = ByteUtil.split(serialized, 1, serialized.length - 1 - SIGNATURE_LENGTH, SIGNATURE_LENGTH);
      byte     version      = messageParts[0][0];
      byte[]   message      = messageParts[1];
      byte[]   signature    = messageParts[2];

      if (ByteUtil.highBitsToInt(version) < 3) {
        throw new LegacyMessageException("Legacy message: " + ByteUtil.highBitsToInt(version));
      }

      if (ByteUtil.highBitsToInt(version) > CURRENT_VERSION) {
        throw new InvalidMessageException("Unknown version: " + ByteUtil.highBitsToInt(version));
      }

      WhisperProtos.SenderKeyMessage senderKeyMessage = WhisperProtos.SenderKeyMessage.parseFrom(message);

      if (!senderKeyMessage.hasId() ||
          !senderKeyMessage.hasIteration() ||
          !senderKeyMessage.hasCiphertext())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized     = serialized;
      this.messageVersion = ByteUtil.highBitsToInt(version);
      this.keyId          = senderKeyMessage.getId();
      this.iteration      = senderKeyMessage.getIteration();
      this.ciphertext     = senderKeyMessage.getCiphertext().toByteArray();
    } catch (InvalidProtocolBufferException | ParseException e) {
      throw new InvalidMessageException(e);
    }
  }

  public SenderKeyMessage(int keyId, int iteration, byte[] ciphertext, ECPrivateKey signatureKey) {
    byte[] version = {ByteUtil.intsToByteHighAndLow(CURRENT_VERSION, CURRENT_VERSION)};
    byte[] message = WhisperProtos.SenderKeyMessage.newBuilder()
                                                   .setId(keyId)
                                                   .setIteration(iteration)
                                                   .setCiphertext(ByteString.copyFrom(ciphertext))
                                                   .build().toByteArray();

    byte[] signature = getSignature(signatureKey, ByteUtil.combine(version, message));

    this.serialized       = ByteUtil.combine(version, message, signature);
    this.messageVersion   = CURRENT_VERSION;
    this.keyId            = keyId;
    this.iteration        = iteration;
    this.ciphertext       = ciphertext;
  }

  public int getKeyId() {
    return keyId;
  }

  public int getIteration() {
    return iteration;
  }

  public byte[] getCipherText() {
    return ciphertext;
  }

  public void verifySignature(ECPublicKey signatureKey)
      throws InvalidMessageException
  {
    try {
      byte[][] parts    = ByteUtil.split(serialized, serialized.length - SIGNATURE_LENGTH, SIGNATURE_LENGTH);

      if (!Curve.verifySignature(signatureKey, parts[0], parts[1])) {
        throw new InvalidMessageException("Invalid signature!");
      }

    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  private byte[] getSignature(ECPrivateKey signatureKey, byte[] serialized) {
    try {
      return Curve.calculateSignature(signatureKey, serialized);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return CiphertextMessage.SENDERKEY_TYPE;
  }
}
