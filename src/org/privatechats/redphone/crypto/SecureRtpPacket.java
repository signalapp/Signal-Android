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

package org.privatechats.redphone.crypto;

import org.privatechats.redphone.network.RtpPacket;

/**
 * A class representing an SRTP packet.
 *
 * @author Moxie Marlinspike
 *
 */

public class SecureRtpPacket extends RtpPacket {

  private static final int MAC_SIZE = 20;

  private long logicalSequence;

  public SecureRtpPacket(int payloadLength) {
    super(payloadLength + MAC_SIZE);
    setVersion();
    setTimeStamp(System.currentTimeMillis());
  }

  public SecureRtpPacket(RtpPacket packet) {
    super(packet.getPacket(), packet.getPacketLength());
  }

  public byte[] getMac() {
    byte[] mac = new byte[MAC_SIZE];
    System.arraycopy(data, packetLength - MAC_SIZE, mac, 0, mac.length);
    return mac;
  }

  public void setMac(byte[] mac) {
    System.arraycopy(mac, 0, this.data, packetLength - MAC_SIZE, mac.length);
  }

  @Override
  public void setPayload(byte[] data, int length) {
    super.setPayload(data, length);
    super.packetLength += MAC_SIZE;
  }

  @Override
  public byte[] getPayload() {
    int payloadLength = packetLength - HEADER_LENGTH - MAC_SIZE;
    byte[] payload    = new byte[payloadLength];
    System.arraycopy(data, HEADER_LENGTH, payload, 0, payloadLength);

    return payload;
  }

  public long getLogicalSequence() {
    return logicalSequence;
  }

  public void setLogicalSequence(long logicalSequence) {
    this.logicalSequence = logicalSequence;
  }

  public byte[] getDataToMac() {
    return data;
  }

  public int getDataToMacLength() {
    return packetLength - MAC_SIZE;
  }


}
