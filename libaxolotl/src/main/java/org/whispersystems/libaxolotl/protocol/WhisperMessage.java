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
package org.whispersystems.libaxolotl.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class WhisperMessage implements CiphertextMessage {

  private static final int MAC_LENGTH = 8;

  private final ECPublicKey senderEphemeral;
  private final int         counter;
  private final int         previousCounter;
  private final byte[]      ciphertext;
  private final byte[]      serialized;

  public WhisperMessage(byte[] serialized) throws InvalidMessageException, LegacyMessageException {
    try {
      byte[][] messageParts = ByteUtil.split(serialized, 1, serialized.length - 1 - MAC_LENGTH, MAC_LENGTH);
      byte     version      = messageParts[0][0];
      byte[]   message      = messageParts[1];
      byte[]   mac          = messageParts[2];

      if (ByteUtil.highBitsToInt(version) <= CiphertextMessage.UNSUPPORTED_VERSION) {
        throw new LegacyMessageException("Legacy message: " + ByteUtil.highBitsToInt(version));
      }

      if (ByteUtil.highBitsToInt(version) != CURRENT_VERSION) {
        throw new InvalidMessageException("Unknown version: " + ByteUtil.highBitsToInt(version));
      }

      WhisperProtos.WhisperMessage whisperMessage = WhisperProtos.WhisperMessage.parseFrom(message);

      if (!whisperMessage.hasCiphertext() ||
          !whisperMessage.hasCounter() ||
          !whisperMessage.hasEphemeralKey())
      {
        throw new InvalidMessageException("Incomplete message.");
      }

      this.serialized      = serialized;
      this.senderEphemeral = Curve.decodePoint(whisperMessage.getEphemeralKey().toByteArray(), 0);
      this.counter         = whisperMessage.getCounter();
      this.previousCounter = whisperMessage.getPreviousCounter();
      this.ciphertext      = whisperMessage.getCiphertext().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    } catch (ParseException e) {
      throw new InvalidMessageException(e);
    }
  }

  public WhisperMessage(SecretKeySpec macKey, ECPublicKey senderEphemeral,
                        int counter, int previousCounter, byte[] ciphertext)
  {
    byte[] version = {ByteUtil.intsToByteHighAndLow(CURRENT_VERSION, CURRENT_VERSION)};
    byte[] message = WhisperProtos.WhisperMessage.newBuilder()
                                   .setEphemeralKey(ByteString.copyFrom(senderEphemeral.serialize()))
                                   .setCounter(counter)
                                   .setPreviousCounter(previousCounter)
                                   .setCiphertext(ByteString.copyFrom(ciphertext))
                                   .build().toByteArray();
    byte[] mac     = getMac(macKey, ByteUtil.combine(version, message));

    this.serialized      = ByteUtil.combine(version, message, mac);
    this.senderEphemeral = senderEphemeral;
    this.counter         = counter;
    this.previousCounter = previousCounter;
    this.ciphertext      = ciphertext;
  }

  public ECPublicKey getSenderEphemeral()  {
    return senderEphemeral;
  }

  public int getCounter() {
    return counter;
  }

  public byte[] getBody() {
    return ciphertext;
  }

  public void verifyMac(SecretKeySpec macKey)
      throws InvalidMessageException
  {
    byte[][] parts    = ByteUtil.split(serialized, serialized.length - MAC_LENGTH, MAC_LENGTH);
    byte[]   ourMac   = getMac(macKey, parts[0]);
    byte[]   theirMac = parts[1];

    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw new InvalidMessageException("Bad Mac!");
    }
  }

  private byte[] getMac(SecretKeySpec macKey, byte[] serialized) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(macKey);

      byte[] fullMac = mac.doFinal(serialized);
      return ByteUtil.trim(fullMac, MAC_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public byte[] serialize() {
    return serialized;
  }

  @Override
  public int getType() {
    return CiphertextMessage.WHISPER_TYPE;
  }

  public static boolean isLegacy(byte[] message) {
    return message != null && message.length >= 1 &&
        ByteUtil.highBitsToInt(message[0]) <= CiphertextMessage.UNSUPPORTED_VERSION;
  }

}
