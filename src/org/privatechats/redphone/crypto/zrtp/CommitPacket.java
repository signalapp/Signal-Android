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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * ZRTP handshake 'Commit' packet.
 *
 * @author Moxie Marlinspike
 *
 */

public class CommitPacket extends HandshakePacket {

  public  static final String TYPE          = "Commit  ";
  private static final int    COMMIT_LENGTH = 116;

  private static final int _LENGTH_OFFSET    = MESSAGE_BASE + 2;
  private static final int _HASH_OFFSET      = MESSAGE_BASE + 12;
  private static final int _ZID_OFFSET       = MESSAGE_BASE + 44;
  private static final int _HASH_SPEC_OFFSET = MESSAGE_BASE + 56;
  private static final int _CIPHER_OFFSET    = MESSAGE_BASE + 60;
  private static final int _AUTH_OFFSET      = MESSAGE_BASE + 64;
  private static final int _AGREEMENT_OFFSET = MESSAGE_BASE + 68;
  private static final int _SAS_OFFSET       = MESSAGE_BASE + 72;
  private static final int _HVI_OFFSET       = MESSAGE_BASE + 76;
  private static final int _MAC_OFFSET       = MESSAGE_BASE + 108;

  private int LENGTH_OFFSET    = _LENGTH_OFFSET;
  private int HASH_OFFSET      = _HASH_OFFSET;
  private int ZID_OFFSET       = _ZID_OFFSET;
  private int HASH_SPEC_OFFSET = _HASH_SPEC_OFFSET;
  private int CIPHER_OFFSET    = _CIPHER_OFFSET;
  private int AUTH_OFFSET      = _AUTH_OFFSET;
  private int AGREEMENT_OFFSET = _AGREEMENT_OFFSET;
  private int SAS_OFFSET       = _SAS_OFFSET;
  private int HVI_OFFSET       = _HVI_OFFSET;
  private int MAC_OFFSET       = _MAC_OFFSET;

  private static final byte[] HASH_SPEC      = {'S', '2', '5', '6'};
  private static final byte[] CIPHER_SPEC    = {'A', 'E', 'S', '1'};
  private static final byte[] AUTH_SPEC      = {'H', 'S', '8', '0'};
  private static final byte[] SAS_SPEC       = {'B', '2', '5', '6'};

  public CommitPacket(RtpPacket packet) {
    super(packet);
    fixOffsetsForHeaderBug();
  }

  public CommitPacket(RtpPacket packet, boolean deepCopy) {
    super(packet, deepCopy);
    fixOffsetsForHeaderBug();
  }

  public CommitPacket(HashChain hashChain, byte[] helloBytes,
                      DHPartTwoPacket dhPacket, byte[] zid,
                      boolean includeLegacyHeaderBug)
    throws InvalidPacketException
  {
    super(TYPE, COMMIT_LENGTH, includeLegacyHeaderBug);
    fixOffsetsForHeaderBug();
    setHash(hashChain.getH2());
    setZID(zid);
    setSpec(dhPacket.getAgreementSpec());
    setHvi(calculateHvi(helloBytes, dhPacket.getMessageBytes()));
    setMac(hashChain.getH1(), MAC_OFFSET, COMMIT_LENGTH - 8);
  }

  public byte[] getHvi() {
    byte[] hvi = new byte[32];
    System.arraycopy(this.data, HVI_OFFSET, hvi, 0, hvi.length);
    return hvi;
  }

  public byte[] getHash() {
    byte[] hash = new byte[32];
    System.arraycopy(this.data, HASH_OFFSET, hash, 0, hash.length);
    return hash;
  }

  public void verifyHvi(byte[] helloBytes, byte[] dhBytes) throws InvalidPacketException {
    byte[] calculatedHvi = calculateHvi(helloBytes, dhBytes);
    byte[] packetHvi     = getHvi();

    if (!Arrays.equals(calculatedHvi, packetHvi))
      throw new InvalidPacketException("HVI doesn't match.");
  }

  public void verifyMac(byte[] key) throws InvalidPacketException {
    super.verifyMac(key, MAC_OFFSET, COMMIT_LENGTH - 8, getHash());
  }

  private void setHash(byte[] hash) {
    System.arraycopy(hash, 0, this.data, HASH_OFFSET, hash.length);
  }

  private void setZID(byte[] zid) {
    System.arraycopy(zid, 0, this.data, ZID_OFFSET, zid.length);
  }

  private void setSpec(byte[] agreementSpec) {
    System.arraycopy(HASH_SPEC, 0, this.data, HASH_SPEC_OFFSET, HASH_SPEC.length);
    System.arraycopy(CIPHER_SPEC, 0, this.data, CIPHER_OFFSET, CIPHER_SPEC.length);
    System.arraycopy(AUTH_SPEC, 0, this.data, AUTH_OFFSET, AUTH_SPEC.length);
    System.arraycopy(agreementSpec, 0, this.data, AGREEMENT_OFFSET, agreementSpec.length);
    System.arraycopy(SAS_SPEC, 0, this.data, SAS_OFFSET, SAS_SPEC.length);
  }

  private byte[] calculateHvi(byte[] helloBytes, byte[] dhBytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(dhBytes);
      md.update(helloBytes);

      return md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void setHvi(byte[] hvi) {
    System.arraycopy(hvi, 0, this.data, HVI_OFFSET, hvi.length);
  }

  public byte[] getKeyAgreementType() {
    byte[] ka = new byte[4];
    System.arraycopy(this.data, AGREEMENT_OFFSET, ka, 0, ka.length);

    return ka;
  }

  private void fixOffsetsForHeaderBug() {
    int headerBugOffset = getHeaderBugOffset();

    LENGTH_OFFSET    += headerBugOffset;
    HASH_OFFSET      += headerBugOffset;
    ZID_OFFSET       += headerBugOffset;
    HASH_SPEC_OFFSET += headerBugOffset;
    CIPHER_OFFSET    += headerBugOffset;
    AUTH_OFFSET      += headerBugOffset;
    AGREEMENT_OFFSET += headerBugOffset;
    SAS_OFFSET       += headerBugOffset;
    HVI_OFFSET       += headerBugOffset;
    MAC_OFFSET       += headerBugOffset;
  }

}
