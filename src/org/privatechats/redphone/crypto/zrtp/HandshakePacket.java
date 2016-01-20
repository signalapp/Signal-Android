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

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Base ZRTP handshake packet, from which all
 * handshake packets derive.
 *
 *
 * @author Moxie Marlinspike
 *
 */

public class HandshakePacket extends RtpPacket {

  private   static final int PREFIX_OFFSET =  0;
  private   static final int COOKIE_OFFSET =  4;
  protected static final int MESSAGE_BASE  = 12;

  private static final int _MAGIC_OFFSET   = MESSAGE_BASE + 0;
  private static final int _LENGTH_OFFSET  = MESSAGE_BASE + 2;
  private static final int _TYPE_OFFSET    = MESSAGE_BASE + 4;

  private int MAGIC_OFFSET  = _MAGIC_OFFSET;
  private int LENGTH_OFFSET = _LENGTH_OFFSET;
  private int TYPE_OFFSET   = _TYPE_OFFSET;

  private static final int  PREFIX_VALUE                   =       0x10;
  private static final int  LEGACY_HEADER_BUG_PREFIX_VALUE =       0x20;
  private static final int  MAGIC_VALUE                    =     0x505a;
  private static final long COOKIE_VALUE                   = 0x5a525450;

  private static final int ZRTP_CRC_LENGTH = 4;

  public HandshakePacket(RtpPacket packet) {
    super(packet.getPacket(), packet.getPacketLength());
    fixOffsetsForHeaderBug();
  }

  public HandshakePacket(RtpPacket packet, boolean deepCopy) {
    super(packet.getPacket(), packet.getPacketLength(), deepCopy);
    fixOffsetsForHeaderBug();
  }

  public HandshakePacket(String type, int length, boolean includeLegacyHeaderBug) {
    super(length + ZRTP_CRC_LENGTH + (includeLegacyHeaderBug ? RtpPacket.HEADER_LENGTH : 0));

    setPrefix(includeLegacyHeaderBug);
    setCookie();
    fixOffsetsForHeaderBug();

    setMagic();
    setLength(length);
    setType(type);
  }

  public byte[] getMessageBytes() throws InvalidPacketException {
    if (this.getPacketLength() < (LENGTH_OFFSET + 3))
      throw new InvalidPacketException("Packet length shorter than length header.");

    int messagePacketLength = this.getLength();

    if (messagePacketLength + 4 > this.getPacketLength())
      throw new InvalidPacketException("Encoded packet length longer than length of packet.");

    byte[] messageBytes = new byte[messagePacketLength];
    System.arraycopy(this.data, getHeaderBugOffset() + MESSAGE_BASE, messageBytes, 0, messagePacketLength);

    return messageBytes;
  }

  private void setPrefix(boolean includeLegacyHeaderBug) {
    if (includeLegacyHeaderBug) data[PREFIX_OFFSET] = LEGACY_HEADER_BUG_PREFIX_VALUE;
    else                        data[PREFIX_OFFSET] = PREFIX_VALUE;
  }

  private void setCookie() {
    Conversions.longTo4ByteArray(this.data, COOKIE_OFFSET, COOKIE_VALUE);
  }

  private int getLength() {
    return Conversions.byteArrayToShort(this.data, LENGTH_OFFSET);
  }

  protected void setLength(int length) {
    Conversions.shortToByteArray(this.data, LENGTH_OFFSET, length);
  }

  private byte[] calculateMac(byte[] key, int messageLength) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      mac.update(this.data, getHeaderBugOffset() + MESSAGE_BASE, messageLength);
      return mac.doFinal();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected void setMac(byte[] key, int macOffset, int messageLength) {
    byte[] digest = calculateMac(key, messageLength);
    System.arraycopy(digest, 0, this.data, macOffset, 8);
  }

  protected void verifyMac(byte[] key, int macOffset, int messageLength, byte[] subhash)
      throws InvalidPacketException
  {
    byte[] digest          = calculateMac(key, messageLength);
    byte[] truncatedDigest = new byte[8];
    byte[] messageDigest   = new byte[8];

    System.arraycopy(digest, 0, truncatedDigest, 0, truncatedDigest.length);
    System.arraycopy(this.data, macOffset, messageDigest, 0, messageDigest.length);

    if (!Arrays.equals(truncatedDigest, messageDigest))
      throw new InvalidPacketException("Bad MAC!");

    if (!verifySubHash(key, subhash))
      throw new InvalidPacketException("MAC key is not preimage of hash included in message!");
  }

  private boolean verifySubHash(byte[] key, byte[] subhash) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest    = md.digest(key);
      return Arrays.equals(digest, subhash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private long calculateCRC(byte[] data, int packetLength) {
    CRC32 crc = new CRC32();
    crc.update(data, 0, packetLength-4);
    return crc.getValue();
  }

  public boolean verifyCRC() {
    long myCRC    = calculateCRC(this.data, getPacketLength());
    long theirCRC = Conversions.byteArray4ToLong(this.data, getPacketLength()-4);
    return myCRC == theirCRC;
  }

  public void setCRC() {
    Conversions.longTo4ByteArray(this.data, getPacketLength()-4,
                                 calculateCRC(this.data, this.getPacketLength()));
  }

  public String getType() {
    if (this.data[PREFIX_OFFSET] != 0x10 && this.data[PREFIX_OFFSET] != 0x20) {
      return ConfAckPacket.TYPE;
    }

    try {
      return new String(data, TYPE_OFFSET, 8, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void setMagic() {
    Conversions.shortToByteArray(this.data, MAGIC_OFFSET, MAGIC_VALUE);
  }

  private void setType(String type) {
    type.getBytes(0, type.length(), this.data, TYPE_OFFSET);
  }

  // NOTE <10-09-2013> :: The RedPhone ZRTP packet format had two problems with it:
  //
  // 1) The wrong bit was set in the 'version' byte (0x20 instead of 0x10).
  //
  // 2) Each handshake packet included 12 extra null bytes in between the ZRTP header
  //    and the ZRTP 'message'.  These don't cause any problems or have any security
  //    implications, but it's definitely incorrect.  In order not to break backwards
  //    compatibility, we have to intentionally do the wrong thing when it looks like
  //    we're talking with old clients.
  //
  // The initiator indicates that it's a "new" client by setting a version of 1 in the
  // initiate signal.  The responder indicates that it's a "new" client by either setting
  // the "new/correct" version byte (0x10) on the handshake packets it sends, or the
  // "old/incorrect" version byte (0x20).
  //
  /// This is a complete mess and it fucks up most of the handshake packet code.  Eventually
  //  we'll phase this out.
  protected int getHeaderBugOffset() {
    if (isLegacyHeaderBugPresent()) {
      Log.w("HandshakePacket", "Returning offset for legacy handshake bug...");
      return RtpPacket.HEADER_LENGTH;
    } else {
      Log.w("HandshakePacket", "Not including legacy handshake bug...");
      return 0;
    }
  }

  public boolean isLegacyHeaderBugPresent() {
    return data[PREFIX_OFFSET] == LEGACY_HEADER_BUG_PREFIX_VALUE;
  }

  private void fixOffsetsForHeaderBug() {
    int headerBugOffset = getHeaderBugOffset();

    MAGIC_OFFSET  += headerBugOffset;
    LENGTH_OFFSET += headerBugOffset;
    TYPE_OFFSET   += headerBugOffset;
  }

}
