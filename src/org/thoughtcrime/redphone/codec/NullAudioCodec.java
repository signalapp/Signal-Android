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

package org.thoughtcrime.redphone.codec;

import org.thoughtcrime.redphone.util.Conversions;

/**
 * An audio codec that does nothing.  Encoded audio is precisely equal
 * to the unencoded audio, except represented in a byte[] instead of a
 * short[].
 *
 * @author Stuart O. Anderson
 */
public class NullAudioCodec extends AudioCodec {

  @Override
  public int decode(byte[] encodedData, short[] rawData, int encLen) {
    for (int i = 0; i < AudioCodec.SAMPLES_PER_FRAME; i++) {
       rawData[i] = Conversions.byteArrayToShort(encodedData, i * 2);
    }
    return encodedPacketSize();

  }

  @Override
  public int encode(short[] rawData, byte[] encodedData, int rawLen) {
    for( int i = 0; i < AudioCodec.SAMPLES_PER_FRAME; i++) {
      Conversions.shortToByteArray(encodedData, i*2, rawData[i]);
    }
    return AudioCodec.SAMPLES_PER_FRAME;
  }

  public int encodedPacketSize() {
    return AudioCodec.SAMPLES_PER_FRAME * 2;
  }
}
