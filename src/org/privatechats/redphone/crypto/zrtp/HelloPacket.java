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

import org.thoughtcrime.redphone.network.RtpPacket;
import org.thoughtcrime.redphone.util.Conversions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ZRTP 'hello' handshake packet.
 *
 * http://tools.ietf.org/html/rfc6189#section-5.2
 *
 * @author Moxie Marlinspike
 *
 */

public class HelloPacket extends HandshakePacket {
  public  static final String TYPE = "Hello   ";

  private static final List<byte[]> KEY_AGREEMENTS = new ArrayList<byte[]>(1) {{
    add(new byte[] {'E', 'C', '2', '5'});
  }};

  private static final int HELLO_MIN_LENGTH       = 88;
  private static final int OPTIONAL_VALUES_LENGTH = KEY_AGREEMENTS.size() * 4;

  private static final int MAGIC_LENGTH   = 2;
  private static final int LENGTH_LENGTH  = 2;
  private static final int TYPE_LENGTH    = 8;
  private static final int VERSION_LENGTH = 4;
  private static final int CLIENT_LENGTH  = 16;
  private static final int H3_LENGTH      = 32;
  private static final int ZID_LENGTH     = 12;
  private static final int MAC_LENGTH     = 8;

  private static final int _LENGTH_OFFSET  = MESSAGE_BASE    + MAGIC_LENGTH;
  private static final int _TYPE_OFFSET    = _LENGTH_OFFSET  + LENGTH_LENGTH;
  private static final int _VERSION_OFFSET = _TYPE_OFFSET    + TYPE_LENGTH;
  private static final int _CLIENT_OFFSET  = _VERSION_OFFSET + VERSION_LENGTH;
  private static final int _H3_OFFSET      = _CLIENT_OFFSET  + CLIENT_LENGTH;
  private static final int _ZID_OFFSET     = _H3_OFFSET      + H3_LENGTH;
  private static final int _FLAGS_OFFSET   = _ZID_OFFSET     + ZID_LENGTH;

  private static final int _HC_OFFSET      = _FLAGS_OFFSET + 1;
  private static final int _CC_OFFSET      = _FLAGS_OFFSET + 2;
  private static final int _AC_OFFSET      = _FLAGS_OFFSET + 2;
  private static final int _KC_OFFSET      = _FLAGS_OFFSET + 3;
  private static final int _SC_OFFSET      = _FLAGS_OFFSET + 3;

  private static final int _OPTIONS_OFFSET = _FLAGS_OFFSET + 4;

  private int LENGTH_OFFSET  = _LENGTH_OFFSET;
  private int TYPE_OFFSET    = _TYPE_OFFSET;
  private int VERSION_OFFSET = _VERSION_OFFSET;
  private int CLIENT_OFFSET  = _CLIENT_OFFSET;
  private int H3_OFFSET      = _H3_OFFSET;
  private int ZID_OFFSET     = _ZID_OFFSET;
  private int FLAGS_OFFSET   = _FLAGS_OFFSET;

  private int HC_OFFSET      = _HC_OFFSET;
  private int CC_OFFSET      = _CC_OFFSET;
  private int AC_OFFSET      = _AC_OFFSET;
  private int KC_OFFSET      = _KC_OFFSET;
  private int SC_OFFSET      = _SC_OFFSET;

  private int OPTIONS_OFFSET = _OPTIONS_OFFSET;

  public HelloPacket(RtpPacket packet) {
    super(packet);
    fixOffsetsForHeaderBug();
  }

  public HelloPacket(RtpPacket packet, boolean deepCopy) {
    super(packet, deepCopy);
    fixOffsetsForHeaderBug();
  }

  public HelloPacket(HashChain hashChain, byte[] zid, boolean includeLegacyHeaderBug) {
    super(TYPE, HELLO_MIN_LENGTH + OPTIONAL_VALUES_LENGTH, includeLegacyHeaderBug);
    fixOffsetsForHeaderBug();
    setZrtpVersion();
    setClientId();
    setH3(hashChain.getH3());
    setZID(zid);
    setKeyAgreement();
    setMac(hashChain.getH2(),
           OPTIONS_OFFSET + OPTIONAL_VALUES_LENGTH,
           HELLO_MIN_LENGTH + OPTIONAL_VALUES_LENGTH - MAC_LENGTH);
  }

  public int getLength() {
    return Conversions.byteArrayToShort(data, LENGTH_OFFSET);
  }

  public byte[] getZID() {
    byte[] zid = new byte[ZID_LENGTH];
    System.arraycopy(this.data, ZID_OFFSET, zid, 0, zid.length);
    return zid;
  }

  private byte[] getH3() {
    byte[] hashValue = new byte[H3_LENGTH];
    System.arraycopy(this.data, H3_OFFSET, hashValue, 0, hashValue.length);

    return hashValue;
  }

  public void verifyMac(byte[] key) throws InvalidPacketException {
    if (getLength() < HELLO_MIN_LENGTH)
      throw new InvalidPacketException("Encoded length longer than data length.");

    super.verifyMac(key,
                    OPTIONS_OFFSET + getOptionsLength(),
                    getMessageLength() - MAC_LENGTH,
                    getH3());
  }

  private int getMessageLength() {
    return HELLO_MIN_LENGTH  + getOptionsLength();
  }

  private int getOptionsLength() {
    return (getHashOptionCount()         * 4) +
           (getCipherOptionCount()       * 4) +
           (getAuthTagOptionCount()      * 4) +
           (getKeyAgreementOptionCount() * 4) +
           (getSasOptionCount()          * 4);
  }

  private int getHashOptionCount() {
    return this.data[HC_OFFSET] & 0x0F;
  }

  private int getCipherOptionCount() {
    return (this.data[CC_OFFSET] & 0xFF) >> 4;
  }

  private void setCipherOptionCount(int count) {
    this.data[CC_OFFSET] |= ((count & 0x0F) << 4);
  }

  private int getAuthTagOptionCount() {
    return this.data[AC_OFFSET] & 0x0F;
  }

  private int getKeyAgreementOptionCount() {
    return (this.data[KC_OFFSET] & 0xFF) >> 4;
  }

  private void setKeyAgreementOptionsCount(int count) {
    this.data[KC_OFFSET] |= ((count << 4) & 0xFF);
  }

  public Set<String> getKeyAgreementOptions() {
    Set<String> keyAgreementOptions = new HashSet<String>();

    int keyAgreementOptionsOffset  = OPTIONS_OFFSET                +
                                     (getHashOptionCount()    * 4) +
                                     (getCipherOptionCount()  * 4) +
                                     (getAuthTagOptionCount() * 4);

    for (int i=0;i<getKeyAgreementOptionCount();i++) {
      int keyAgreementOptionOffset = keyAgreementOptionsOffset + (i * 4);
      keyAgreementOptions.add(new String(this.data, keyAgreementOptionOffset, 4));
    }

    return keyAgreementOptions;
  }

  public void setKeyAgreementOptions(List<byte[]> options) {
    int keyAgreementOptionsOffset  = OPTIONS_OFFSET                +
        (getHashOptionCount()    * 4) +
        (getCipherOptionCount()  * 4) +
        (getAuthTagOptionCount() * 4);

    for (int i=0;i<options.size();i++) {
      int    optionOffset = keyAgreementOptionsOffset + (i * 4);
      byte[] option       = options.get(i);
      System.arraycopy(option, 0, this.data, optionOffset, 4);
    }
  }

  private int getSasOptionCount() {
    return this.data[SC_OFFSET] & 0x0F;
  }

  private void setZrtpVersion() {
    "1.10".getBytes(0, 4, this.data, VERSION_OFFSET);
  }

  private void setClientId() {
    "RedPhone 024    ".getBytes(0, 16, this.data, CLIENT_OFFSET);
  }

  public String getClientId() {
    return new String(this.data, CLIENT_OFFSET, CLIENT_LENGTH);
  }

  private void setH3(byte[] hash) {
    System.arraycopy(hash, 0, this.data, H3_OFFSET, hash.length);
  }

  private void setZID(byte[] zid) {
    System.arraycopy(zid, 0, this.data, ZID_OFFSET, zid.length);
  }

  private void setKeyAgreement() {
    setKeyAgreementOptionsCount(KEY_AGREEMENTS.size());
    setKeyAgreementOptions(KEY_AGREEMENTS);
  }

  private void fixOffsetsForHeaderBug() {
    int headerBugOffset = getHeaderBugOffset();

    LENGTH_OFFSET  += headerBugOffset;
    TYPE_OFFSET    += headerBugOffset;
    VERSION_OFFSET += headerBugOffset;
    CLIENT_OFFSET  += headerBugOffset;
    H3_OFFSET      += headerBugOffset;
    ZID_OFFSET     += headerBugOffset;
    FLAGS_OFFSET   += headerBugOffset;

    HC_OFFSET      += headerBugOffset;
    CC_OFFSET      += headerBugOffset;
    AC_OFFSET      += headerBugOffset;
    KC_OFFSET      += headerBugOffset;
    SC_OFFSET      += headerBugOffset;

    OPTIONS_OFFSET += headerBugOffset;
  }
}
