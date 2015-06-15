/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.whispersystems.textsecure.api.messages;

import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.logging.Log;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.Envelope;
import org.whispersystems.textsecure.internal.util.Base64;
import org.whispersystems.textsecure.internal.util.Hex;

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
 * This class represents an encrypted TextSecure envelope.
 *
 * The envelope contains the wrapping information, such as the sender, the
 * message timestamp, the encrypted message type, etc.
 *
  * @author  Moxie Marlinspike
 */
public class TextSecureEnvelope {

  private static final String TAG = TextSecureEnvelope.class.getSimpleName();

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
   * Construct an envelope from a serialized, Base64 encoded TextSecureEnvelope, encrypted
   * with a signaling key.
   *
   * @param message The serialized TextSecureEnvelope, base64 encoded and encrypted.
   * @param signalingKey The signaling key.
   * @throws IOException
   * @throws InvalidVersionException
   */
  public TextSecureEnvelope(String message, String signalingKey)
      throws IOException, InvalidVersionException
  {
    this(Base64.decode(message), signalingKey);
  }

  /**
   * Construct an envelope from a serialized TextSecureEnvelope, encrypted with a signaling key.
   *
   * @param ciphertext The serialized and encrypted TextSecureEnvelope.
   * @param signalingKey The signaling key.
   * @throws InvalidVersionException
   * @throws IOException
   */
  public TextSecureEnvelope(byte[] ciphertext, String signalingKey)
      throws InvalidVersionException, IOException
  {
    if (ciphertext.length < VERSION_LENGTH || ciphertext[VERSION_OFFSET] != SUPPORTED_VERSION)
      throw new InvalidVersionException("Unsupported version!");

    SecretKeySpec cipherKey  = getCipherKey(signalingKey);
    SecretKeySpec macKey     = getMacKey(signalingKey);

    verifyMac(ciphertext, macKey);

    this.envelope = Envelope.parseFrom(getPlaintext(ciphertext, cipherKey));
  }

  public TextSecureEnvelope(int type, String source, int sourceDevice,
                            String relay, long timestamp,
                            byte[] legacyMessage, byte[] content)
  {
    Envelope.Builder builder = Envelope.newBuilder()
                                       .setType(Envelope.Type.valueOf(type))
                                       .setSource(source)
                                       .setSourceDevice(sourceDevice)
                                       .setRelay(relay)
                                       .setTimestamp(timestamp);

    if (legacyMessage != null) builder.setLegacyMessage(ByteString.copyFrom(legacyMessage));
    if (content != null)       builder.setContent(ByteString.copyFrom(content));

    this.envelope = builder.build();
  }

  /**
   * @return The envelope's sender.
   */
  public String getSource() {
    return envelope.getSource();
  }

  /**
   * @return The envelope's sender device ID.
   */
  public int getSourceDevice() {
    return envelope.getSourceDevice();
  }

  /**
   * @return The envelope's sender as a TextSecureAddress.
   */
  public TextSecureAddress getSourceAddress() {
    return new TextSecureAddress(envelope.getSource(),
                                 envelope.hasRelay() ? Optional.fromNullable(envelope.getRelay()) :
                                                     Optional.<String>absent());
  }

  /**
   * @return The envelope content type.
   */
  public int getType() {
    return envelope.getType().getNumber();
  }

  /**
   * @return The federated server this envelope came from.
   */
  public String getRelay() {
    return envelope.getRelay();
  }

  /**
   * @return The timestamp this envelope was sent.
   */
  public long getTimestamp() {
    return envelope.getTimestamp();
  }

  /**
   * @return Whether the envelope contains a TextSecureDataMessage
   */
  public boolean hasLegacyMessage() {
    return envelope.hasLegacyMessage();
  }

  /**
   * @return The envelope's containing TextSecure message.
   */
  public byte[] getLegacyMessage() {
    return envelope.getLegacyMessage().toByteArray();
  }

  /**
   * @return Whether the envelope contains an encrypted TextSecureContent
   */
  public boolean hasContent() {
    return envelope.hasContent();
  }

  /**
   * @return The envelope's encrypted TextSecureContent.
   */
  public byte[] getContent() {
    return envelope.getContent().toByteArray();
  }

  /**
   * @return true if the containing message is a {@link org.whispersystems.libaxolotl.protocol.WhisperMessage}
   */
  public boolean isWhisperMessage() {
    return envelope.getType().getNumber() == Envelope.Type.CIPHERTEXT_VALUE;
  }

  /**
   * @return true if the containing message is a {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage}
   */
  public boolean isPreKeyWhisperMessage() {
    return envelope.getType().getNumber() == Envelope.Type.PREKEY_BUNDLE_VALUE;
  }

  /**
   * @return true if the containing message is a delivery receipt.
   */
  public boolean isReceipt() {
    return envelope.getType().getNumber() == Envelope.Type.RECEIPT_VALUE;
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
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException e) {
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
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
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
