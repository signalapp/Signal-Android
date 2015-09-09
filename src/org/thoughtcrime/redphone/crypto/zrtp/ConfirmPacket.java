/*
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

package org.thoughtcrime.redphone.crypto.zrtp;

import android.util.Log;

import org.thoughtcrime.redphone.network.RtpPacket;
import org.thoughtcrime.redphone.util.Conversions;
import org.thoughtcrime.redphone.util.Hex;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Confirm ZRTP handshake packet.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConfirmPacket extends HandshakePacket {

  private static final int CONFIRM_LENGTH   = 76;
  private static final int ENCRYPTED_LENGTH = 40;

  private static final int _LENGTH_OFFSET   = MESSAGE_BASE + 2;
  private static final int _HMAC_OFFSET     = MESSAGE_BASE + 12;
  private static final int _IV_OFFSET       = MESSAGE_BASE + 20;
  private static final int _PREIMAGE_OFFSET = MESSAGE_BASE + 36;
  private static final int _CACHE_OFFSET    = MESSAGE_BASE + 72;

  private int LENGTH_OFFSET   = _LENGTH_OFFSET;
  private int HMAC_OFFSET     = _HMAC_OFFSET;
  private int IV_OFFSET       = _IV_OFFSET;
  private int PREIMAGE_OFFSET = _PREIMAGE_OFFSET;
  private int CACHE_OFFSET    = _CACHE_OFFSET;

  private final boolean includeLegacyConfirmPacketBug;

  public ConfirmPacket(RtpPacket packet, boolean includeLegacyConfirmPacketBug) {
    super(packet);
    this.includeLegacyConfirmPacketBug = includeLegacyConfirmPacketBug;
    fixOffsetsForHeaderBug();
  }

  public ConfirmPacket(String type, byte[] macKey, byte[] cipherKey,
                       HashChain hashChain,
                       boolean includeLegacyConfirmPacketBug,
                       boolean includeLegacyHeaderBug)
  {
    super(type, CONFIRM_LENGTH, includeLegacyHeaderBug);
    this.includeLegacyConfirmPacketBug = includeLegacyConfirmPacketBug;
    fixOffsetsForHeaderBug();
    setPreimage(hashChain.getH0());
    setCacheTime();
    setIv();
    computeCipherOperation(cipherKey, Cipher.ENCRYPT_MODE);
    setMac(macKey);
  }

  public void verifyMac(byte[] macKey) throws InvalidPacketException {
    if (this.getPacketLength() - PREIMAGE_OFFSET < ENCRYPTED_LENGTH)
      throw new InvalidPacketException("Confirm packet too short.");

    byte[] digest          = calculateMac(macKey);
    byte[] truncatedDigest = new byte[8];
    System.arraycopy(digest, 0, truncatedDigest, 0, truncatedDigest.length);

    byte[] givenDigest     = getMac();

    Log.w("ConfirmPacket", "Given Digest: " + Hex.toString(givenDigest));
    Log.w("ConfirmPacket", "Calcu Digest: " + Hex.toString(digest));

    if (!Arrays.equals(truncatedDigest, givenDigest))
      throw new InvalidPacketException("HMAC doesn't match!");
  }

  public void decrypt(byte[] cipherKey) {
    computeCipherOperation(cipherKey, Cipher.DECRYPT_MODE);
  }

  public byte[] getPreimage() {
    byte[] preimage = new byte[32];
    System.arraycopy(this.data, PREIMAGE_OFFSET, preimage, 0, preimage.length);
    return preimage;
  }

  private byte[] calculateMac(byte[] macKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
      mac.update(this.data, PREIMAGE_OFFSET, ENCRYPTED_LENGTH);

      return mac.doFinal();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void setMac(byte[] macKey) {
    byte[] digest = calculateMac(macKey);
    System.arraycopy(digest, 0, this.data, HMAC_OFFSET, 8);
  }

  private byte[] getMac() {
    byte[] digest = new byte[8];
    System.arraycopy(this.data, HMAC_OFFSET, digest, 0, digest.length);
    return digest;
  }

  private byte[] getIv() {
    byte[] iv = new byte[16];
    System.arraycopy(this.data, IV_OFFSET, iv, 0, iv.length);
    return iv;
  }

  private void setIv() {
    try {
      byte[] iv = new byte[16];
      if (!includeLegacyConfirmPacketBug) { // Temporary implementation bug compatibility issue.  See note in ZRTPSocket.
        SecureRandom.getInstance("SHA1PRNG").nextBytes(iv);
      }
      System.arraycopy(iv, 0, this.data, IV_OFFSET, iv.length);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void computeCipherOperation(byte[] cipherKey, int mode) {
    try {
      IvParameterSpec ivSpec = new IvParameterSpec(getIv());
      SecretKeySpec keySpec  = new SecretKeySpec(cipherKey, "AES");
      Cipher cipher          = Cipher.getInstance("AES/CFB/NoPadding");
      cipher.init(mode, keySpec, ivSpec);

      byte[] encryptedData = cipher.doFinal(this.data, PREIMAGE_OFFSET, ENCRYPTED_LENGTH);
      System.arraycopy(encryptedData, 0, this.data, PREIMAGE_OFFSET, ENCRYPTED_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new IllegalArgumentException(e);
    } catch (NoSuchPaddingException e) {
      throw new IllegalArgumentException(e);
    } catch (IllegalBlockSizeException e) {
      throw new IllegalArgumentException(e);
    } catch (BadPaddingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void setCacheTime() {
    Conversions.longTo4ByteArray(this.data, CACHE_OFFSET, 0xffffffffL);
  }

  private void setPreimage(byte[] preimage) {
    System.arraycopy(preimage, 0, this.data, PREIMAGE_OFFSET, preimage.length);
  }

  public long getCacheTime() {
    byte[] cacheTime = new byte[4];
    System.arraycopy(this.data, CACHE_OFFSET, cacheTime, 0, cacheTime.length);
    return Conversions.byteArray4ToLong(cacheTime, 0);
  }

  private void fixOffsetsForHeaderBug() {
    int headerBugOffset = getHeaderBugOffset();

    LENGTH_OFFSET   += headerBugOffset;
    HMAC_OFFSET     += headerBugOffset;
    IV_OFFSET       += headerBugOffset;
    PREIMAGE_OFFSET += headerBugOffset;
    CACHE_OFFSET    += headerBugOffset;
  }
}
