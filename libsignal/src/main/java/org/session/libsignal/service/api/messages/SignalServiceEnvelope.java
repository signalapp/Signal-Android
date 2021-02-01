/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.messages;

import com.google.protobuf.ByteString;

import org.session.libsignal.libsignal.InvalidVersionException;
import org.session.libsignal.libsignal.logging.Log;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Envelope;
import org.session.libsignal.utilities.Base64;
import org.session.libsignal.utilities.Hex;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class represents an encrypted Signal Service envelope.
 *
 * The envelope contains the wrapping information, such as the sender, the
 * message timestamp, the encrypted message type, etc.
 *
  * @author  Moxie Marlinspike
 */
public class SignalServiceEnvelope {

  private static final String TAG = SignalServiceEnvelope.class.getSimpleName();

  private static final int SUPPORTED_VERSION =  1;
  private static final int CIPHER_KEY_SIZE   = 32;
  private static final int MAC_KEY_SIZE      = 20;
  private static final int MAC_SIZE          = 10;

  private static final int VERSION_OFFSET    =  0;
  private static final int VERSION_LENGTH    =  1;
  private static final int IV_OFFSET         = VERSION_OFFSET + VERSION_LENGTH;
  private static final int IV_LENGTH         = 16;
  private static final int CIPHERTEXT_OFFSET = IV_OFFSET + IV_LENGTH;

  private final Envelope envelope;

  /**
   * Construct an envelope from a serialized, Base64 encoded SignalServiceEnvelope, encrypted
   * with a signaling key.
   *
   * @param message The serialized SignalServiceEnvelope, base64 encoded and encrypted.
   * @param signalingKey The signaling key.
   * @throws IOException
   * @throws InvalidVersionException
   */
  public SignalServiceEnvelope(String message, String signalingKey, boolean isSignalingKeyEncrypted)
      throws IOException, InvalidVersionException
  {
    this(Base64.decode(message), signalingKey, isSignalingKeyEncrypted);
  }

  /**
   * Construct an envelope from a serialized SignalServiceEnvelope, encrypted with a signaling key.
   *
   * @param input The serialized and (optionally) encrypted SignalServiceEnvelope.
   * @param signalingKey The signaling key.
   * @throws InvalidVersionException
   * @throws IOException
   */
  public SignalServiceEnvelope(byte[] input, String signalingKey, boolean isSignalingKeyEncrypted)
      throws InvalidVersionException, IOException
  {
    if (!isSignalingKeyEncrypted) {
      this.envelope = Envelope.parseFrom(input);
    } else {
      if (input.length < VERSION_LENGTH || input[VERSION_OFFSET] != SUPPORTED_VERSION) {
        throw new InvalidVersionException("Unsupported version!");
      }

      SecretKeySpec cipherKey = getCipherKey(signalingKey);
      SecretKeySpec macKey    = getMacKey(signalingKey);

      verifyMac(input, macKey);

      this.envelope = Envelope.parseFrom(getPlaintext(input, cipherKey));
    }
  }

  public SignalServiceEnvelope(Envelope proto) {
    Envelope.Builder builder = Envelope.newBuilder();
    builder.setType(Envelope.Type.valueOf(proto.getType().getNumber()));
    if (proto.getSource() != null) {
      builder.setSource(proto.getSource());
    }
    if (proto.getSourceDevice() > 0) {
      builder.setSourceDevice(proto.getSourceDevice());
    }
    builder.setTimestamp(proto.getTimestamp());
    builder.setServerTimestamp(proto.getServerTimestamp());
    if (proto.getServerGuid() != null) {
      builder.setServerGuid(proto.getServerGuid());
    }
    if (proto.getLegacyMessage() != null) {
      builder.setLegacyMessage(ByteString.copyFrom(proto.getLegacyMessage().toByteArray()));
    }
    if (proto.getContent() != null) {
      builder.setContent(ByteString.copyFrom(proto.getContent().toByteArray()));
    }
    this.envelope = builder.build();
  }

  public SignalServiceEnvelope(int type, String sender, int senderDevice, long timestamp, byte[] legacyMessage, byte[] content, long serverTimestamp, String uuid) {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setSource(sender)
                                       .setSourceDevice(senderDevice)
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverTimestamp);

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (legacyMessage != null) builder.setLegacyMessage(ByteString.copyFrom(legacyMessage));
    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope = builder.build();
  }

  public SignalServiceEnvelope(int type, long timestamp, byte[] legacyMessage, byte[] content, long serverTimestamp, String uuid) {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setTimestamp(timestamp)
                                       .setServerTimestamp(serverTimestamp);

    if (uuid != null) {
      builder.setServerGuid(uuid);
    }

    if (legacyMessage != null) builder.setLegacyMessage(ByteString.copyFrom(legacyMessage));
    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope = builder.build();
  }

  public String getUuid() {
    return envelope.getServerGuid();
  }

  public boolean hasUuid() {
    return envelope.hasServerGuid();
  }

  public boolean hasSource() {
    return envelope.hasSource() && envelope.getSource().length() > 0;
  }

  /**
   * @return The envelope's sender.
   */
  public String getSource() {
    return envelope.getSource();
  }

  public boolean hasSourceDevice() {
    return envelope.hasSourceDevice();
  }

  /**
   * @return The envelope's sender device ID.
   */
  public int getSourceDevice() {
    return envelope.getSourceDevice();
  }

  /**
   * @return The envelope's sender as a SignalServiceAddress.
   */
  public SignalServiceAddress getSourceAddress() {
    return new SignalServiceAddress(envelope.getSource());
  }

  /**
   * @return The envelope content type.
   */
  public int getType() {
    return envelope.getType().getNumber();
  }

  /**
   * @return The timestamp this envelope was sent.
   */
  public long getTimestamp() {
    return envelope.getTimestamp();
  }

  public long getServerTimestamp() {
    return envelope.getServerTimestamp();
  }

  /**
   * @return Whether the envelope contains a SignalServiceDataMessage
   */
  public boolean hasLegacyMessage() {
    return envelope.hasLegacyMessage();
  }

  /**
   * @return The envelope's containing SignalService message.
   */
  public byte[] getLegacyMessage() {
    return envelope.getLegacyMessage().toByteArray();
  }

  /**
   * @return Whether the envelope contains an encrypted SignalServiceContent
   */
  public boolean hasContent() {
    return envelope.hasContent();
  }

  /**
   * @return The envelope's encrypted SignalServiceContent.
   */
  public byte[] getContent() {
    return envelope.getContent().toByteArray();
  }

  /**
   * @return true if the containing message is a {@link org.session.libsignal.libsignal.protocol.SignalMessage}
   */
  public boolean isSignalMessage() {
    return envelope.getType().getNumber() == Envelope.Type.CIPHERTEXT_VALUE;
  }

  /**
   * @return true if the containing message is a {@link org.session.libsignal.libsignal.protocol.PreKeySignalMessage}
   */
  public boolean isPreKeySignalMessage() {
    return envelope.getType().getNumber() == Envelope.Type.PREKEY_BUNDLE_VALUE;
  }

  /**
   * @return true if the containing message is a delivery receipt.
   */
  public boolean isReceipt() {
    return envelope.getType().getNumber() == Envelope.Type.RECEIPT_VALUE;
  }

  public boolean isUnidentifiedSender() {
    return envelope.getType().getNumber() == Envelope.Type.UNIDENTIFIED_SENDER_VALUE;
  }

  public boolean isFallbackMessage() {
    return envelope.getType().getNumber() == Envelope.Type.FALLBACK_MESSAGE_VALUE;
  }

  public boolean isClosedGroupCiphertext() {
      return envelope.getType().getNumber() == Envelope.Type.CLOSED_GROUP_CIPHERTEXT_VALUE;
  }

  private byte[] getPlaintext(byte[] ciphertext, SecretKeySpec cipherKey) throws IOException {
    try {
      byte[] ivBytes = new byte[IV_LENGTH];
      System.arraycopy(ciphertext, IV_OFFSET, ivBytes, 0, ivBytes.length);
      IvParameterSpec iv = new IvParameterSpec(ivBytes);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv);

      return cipher.doFinal(ciphertext, CIPHERTEXT_OFFSET,
                            ciphertext.length - VERSION_LENGTH - IV_LENGTH - MAC_SIZE);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      Log.w(TAG, e);
      throw new IOException("Bad padding?");
    }
  }

  private void verifyMac(byte[] ciphertext, SecretKeySpec macKey) throws IOException {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      if (ciphertext.length < MAC_SIZE + 1)
        throw new IOException("Invalid MAC!");

      mac.update(ciphertext, 0, ciphertext.length - MAC_SIZE);

      byte[] ourMacFull  = mac.doFinal();
      byte[] ourMacBytes = new byte[MAC_SIZE];
      System.arraycopy(ourMacFull, 0, ourMacBytes, 0, ourMacBytes.length);

      byte[] theirMacBytes = new byte[MAC_SIZE];
      System.arraycopy(ciphertext, ciphertext.length-MAC_SIZE, theirMacBytes, 0, theirMacBytes.length);

      Log.w(TAG, "Our MAC: " + Hex.toString(ourMacBytes));
      Log.w(TAG, "Thr MAC: " + Hex.toString(theirMacBytes));

      if (!Arrays.equals(ourMacBytes, theirMacBytes)) {
        throw new IOException("Invalid MAC compare!");
      }
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }


  private SecretKeySpec getCipherKey(String signalingKey) throws IOException {
    byte[] signalingKeyBytes = Base64.decode(signalingKey);
    byte[] cipherKey         = new byte[CIPHER_KEY_SIZE];
    System.arraycopy(signalingKeyBytes, 0, cipherKey, 0, cipherKey.length);

    return new SecretKeySpec(cipherKey, "AES");
  }


  private SecretKeySpec getMacKey(String signalingKey) throws IOException {
    byte[] signalingKeyBytes = Base64.decode(signalingKey);
    byte[] macKey            = new byte[MAC_KEY_SIZE];
    System.arraycopy(signalingKeyBytes, CIPHER_KEY_SIZE, macKey, 0, macKey.length);

    return new SecretKeySpec(macKey, "HmacSHA256");
  }

}
