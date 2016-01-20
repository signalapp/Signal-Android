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

package org.privatechats.redphone.network;

import org.privatechats.redphone.util.Conversions;

/**
 * This class builds an RTP packet
 * reference: rfc3550
 *
 * warning: CSRC's are not supported CSRC count _must_ be zero
 *
 * @author Stuart O. Anderson
 */
public class RtpPacket {

  private   static final int PACKET_VERSION =  2;
  protected static final int HEADER_LENGTH  = 12;

  protected byte data[];
  protected int packetLength;

  public RtpPacket( int payloadLength ) {
    packetLength = payloadLength + HEADER_LENGTH;
    data         = new byte[packetLength];
  }

  public RtpPacket(byte[] data, int packetLength) {
    this.data         = data;
    this.packetLength = packetLength;
  }

  public RtpPacket(byte[] data, int packetLength, boolean deepCopy) {
    this.data         = new byte[packetLength];
    this.packetLength = packetLength;
    System.arraycopy(data, 0, this.data, 0, packetLength);
  }

  public void setVersion(){
    data[0]	= (byte) ((data[0] & 0x3F) | ((PACKET_VERSION & 0x03) << 6 ));
  }

  public int getVersion() {
    return ((data[0] & 0xC0) >> 6);
  }

  public void setPadding( boolean bit ) {
    data[0] = Conversions.setBit( data[0], 5, bit );
  }

  public boolean getPadding() {
    return Conversions.getBit( data[0], 5 );
  }

  public void setExtension( boolean bit ) {
    data[0] = Conversions.setBit( data[0], 4, bit );
  }
  public boolean getExtension() {
    return Conversions.getBit( data[0], 4 );
  }

  public void setCSRCCount( int count ) {
    data[0] = (byte) ((data[0] & 0xF0) | ((count & 0x0F)));
  }

  public int getCSRCCount( ) {
    return ( data[0] & 0x0F );
  }

  public void setMarkerBit( boolean bit ){
    data[1] = Conversions.setBit( data[1], 7, bit );
  }

  public boolean getMarkerBit( ) {
    return Conversions.getBit( data[1], 7 );
  }

  public void setPayloadType( int type ){
    data[1] = (byte) ((data[1] & 0x80) | (type & 0x7F));
  }

  public int getPayloadType() {
    return (data[1] & 0x7F);
  }

  public void setSequenceNumber( int seqNum ){
    Conversions.shortToByteArray(data, 2, seqNum);
  }

  public int getSequenceNumber( ) {
    return Conversions.byteArrayToShort(data, 2);
  }

  public void setTimeStamp( long timestamp ) {
    Conversions.longTo4ByteArray(data, 4, timestamp);
  }

  public long getTimeStamp( ) {
    return Conversions.byteArray4ToLong( data, 4 );
  }

  public void setSSRC( long ssrc ) {
    Conversions.longTo4ByteArray(data, 8, ssrc);
  }

  public long getSSRC( ) {
    return Conversions.byteArray4ToLong( data, 8);
  }

  //not supported for now
  public void addCSRC( long csrc ) {
  }

  public void setPayload(byte[] payload) {
    setPayload(payload, payload.length);
  }

  public void setPayload( byte [] payload, int len ){
    System.arraycopy(payload, 0, data, HEADER_LENGTH, len);
    packetLength = len + HEADER_LENGTH;
  }

  public byte[] getPayload(){
    int payloadLen = packetLength - HEADER_LENGTH;
    byte[] result  = new byte[payloadLen];

    System.arraycopy(data, HEADER_LENGTH, result, 0, payloadLen);
    return result;
  }

  public byte[] getPacket() {
    return data;
  }

  public int getPacketLength() {
    return packetLength;
  }
}
