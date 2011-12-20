/** 
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
package org.thoughtcrime.securesms.mms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.crypto.TransportDetails;
import org.thoughtcrime.securesms.util.Conversions;

import android.util.Log;

public class PngTransport implements TransportDetails {

  private static final int CRC_LENGTH          = 4;
  private static final int IHDR_WIDTH_OFFSET   = 8;
  private static final int IHDR_HEIGHT_OFFSET  = 12;
  private static final int IDAT_HEADER_LENGTH  = 8;
	
  private static final byte[] PNG_HEADER = {(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A};
  private static final byte[] IHDR_CHUNK = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0D, (byte)0x49, (byte)0x48, (byte)0x44, (byte)0x52, 
					    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
					    (byte)0x10, (byte)0x06, (byte)0x00, (byte)0x00, (byte)0x00};
  private static final byte[] IEND_CHUNK = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x49, (byte)0x45, (byte)0x4E, (byte)0x44};
  private static final byte[] IDAT_TAG   = {(byte)0x49, (byte)0x44, (byte)0x41, (byte)0x54};
	
  private static final int BYTES_PER_ROW = 640 * 8;
  private static final int IDAT_LENGTH_OFFSET = 33;
	
  private void readFully(InputStream in, byte[] buffer) throws IOException {
    int totalRead = 0;
    int read      = 0;
		
    while (totalRead < buffer.length) {
      read = in.read(buffer, totalRead, buffer.length-totalRead);
      Log.w("PngTransport", "Read: " + read);
      if (read == -1)
	throw new IOException("Could not fill buffer!");
      totalRead+=read;
    }
  }
	
  private int getImageHeight(byte[] data) {
    int rows = (data.length / BYTES_PER_ROW);
    assert(data.length % BYTES_PER_ROW == 0);
    return rows;
  }

  private byte[] getIhdr(int width, int height) {
    byte[] ihdr = new byte[IHDR_CHUNK.length];
    System.arraycopy(IHDR_CHUNK, 0, ihdr, 0, ihdr.length);
    Conversions.intToByteArray(ihdr, IHDR_WIDTH_OFFSET, width);
    Conversions.intToByteArray(ihdr, IHDR_HEIGHT_OFFSET, height);
    return ihdr;
  }
	
  private byte[] calculateChunkCrc(byte[] header, byte[] chunk) {
    byte[] crcBytes = new byte[4];
    CRC32 crc       = new CRC32();
    crc.update(header, 4, header.length - 4);

    if (chunk != null)
      crc.update(chunk, 0, chunk.length);
		
    long crcValue   = crc.getValue();
    Conversions.longTo4ByteArray(crcBytes, 0, crcValue);
		
    return crcBytes;		
  }
	
  private int readIhdr(ByteArrayInputStream bais) throws IOException {
    byte[] ihdr = new byte[IHDR_CHUNK.length];
    bais.read(ihdr);
    return Conversions.byteArrayToInt(ihdr, IHDR_HEIGHT_OFFSET);
  }
	
  private byte[] readIdatChunk(ByteArrayInputStream bais, int rows) throws IOException {
    InflaterInputStream iis    = new InflaterInputStream(bais);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();		
    byte[] buffer              = new byte[BYTES_PER_ROW + 1];
		
    Log.w("PngTransport", "Total image height: " + rows);
		
    for (int i=0;i<rows;i++) {
      readFully(iis, buffer);
      Log.w("PngTransport", "Read row: " + i);
      baos.write(buffer, 1, buffer.length-1);
    }
		
    return baos.toByteArray();
  }
	
  private byte[] getIdatChunkHeader(byte[] chunk) {
    byte[] headerBytes = new byte[8];
    Conversions.intToByteArray(headerBytes, 0, chunk.length);
    System.arraycopy(IDAT_TAG, 0, headerBytes, 4, IDAT_TAG.length);
    return headerBytes;
  }
	
  private byte[] getIdatChunk(byte[] data) {
    try {
      int rows  = (data.length / BYTES_PER_ROW);
			
      assert(data.length % BYTES_PER_ROW == 0);
      Log.w("PngTransport", "data % bytes_per_row: " + (data.length % BYTES_PER_ROW));
			
      ByteArrayOutputStream baos = new ByteArrayOutputStream((rows * BYTES_PER_ROW) + rows);
      DeflaterOutputStream dos   = new DeflaterOutputStream(baos);
			
      for (int i=0;i<rows;i++) {
	dos.write((byte)0x00);
	dos.write(data, (i*BYTES_PER_ROW), BYTES_PER_ROW);
      }
			
      dos.close();
			
      return baos.toByteArray();
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
	
  public byte[] encodeMessage(byte[] data) {
    try {
      int height             = getImageHeight(data);
      byte[] idatChunk       = getIdatChunk(data);
      byte[] idatChunkHeader = getIdatChunkHeader(idatChunk);
      byte[] ihdrChunk       = getIhdr(640, height);
			
      ByteArrayOutputStream baos = new ByteArrayOutputStream(PNG_HEADER.length + IHDR_CHUNK.length + CRC_LENGTH + 
							     idatChunkHeader.length + idatChunk.length + CRC_LENGTH + 
							     IEND_CHUNK.length + CRC_LENGTH);
      baos.write(PNG_HEADER);
      baos.write(ihdrChunk);
      baos.write(calculateChunkCrc(ihdrChunk, null));
      baos.write(idatChunkHeader);
      baos.write(idatChunk);
      baos.write(calculateChunkCrc(idatChunkHeader, idatChunk));
      baos.write(IEND_CHUNK);
      baos.write(calculateChunkCrc(IEND_CHUNK, null));
			
      return baos.toByteArray();			
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }

  public byte[] decodeMessage(byte[] encodedMessageBytes) throws IOException {
    if (encodedMessageBytes.length < IDAT_LENGTH_OFFSET + 5)
      throw new IOException("Encoded bytes too short!");
		
    ByteArrayInputStream bais = new ByteArrayInputStream(encodedMessageBytes);
    bais.skip(PNG_HEADER.length);
    int rows = readIhdr(bais);
    bais.skip(CRC_LENGTH);
    bais.skip(IDAT_HEADER_LENGTH);
		
    return readIdatChunk(bais, rows);
  }

  public byte[] getPaddedMessageBody(byte[] messageBody) {
    int rows             = ((SessionCipher.ENCRYPTED_MESSAGE_OVERHEAD + messageBody.length) / BYTES_PER_ROW) + 1;
    byte[] paddedMessage = new byte[(rows * BYTES_PER_ROW) - SessionCipher.ENCRYPTED_MESSAGE_OVERHEAD];
		
    System.arraycopy(messageBody, 0, paddedMessage, 0, messageBody.length);
    paddedMessage[messageBody.length] = (byte)0x01;
		
    return paddedMessage;
  }

  public byte[] stripPaddedMessage(byte[] messageWithPadding) {
    int i;
		
    for (i=messageWithPadding.length-1;i>=0;i--) {
      if (messageWithPadding[i] == (byte)0x01)
	break;
      else if (i == 0)
	throw new AssertionError("No padding!");
    }
		
    int paddingLength = messageWithPadding.length - i;
    byte[] message    = new byte[messageWithPadding.length - paddingLength];
		
    System.arraycopy(messageWithPadding, 0, message, 0, message.length);		
    return message;
  }
}
