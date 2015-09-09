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


/**
 * Provides the basic interface for all audio codecs as well as default implementations
 * for some less common methods.  .
 *
 * New codecs need to be added to this static factory method in this class.
 *
 * @author Stuart O. Anderson
 */
public abstract class AudioCodec {
  private final static String TAG = "AudioCodec";
  public static int SAMPLE_RATE = 8000;
  public static int FRAME_RATE = 50;
  public static int SAMPLES_PER_FRAME = SAMPLE_RATE/FRAME_RATE;

  //returns the number of raw samples written to rawData
  public abstract int decode( byte [] encodedData, short [] rawData, int encodedBytes );

  //returns the number of encoded bytes written to encodedData
  public abstract int encode( short [] rawData, byte [] encodedData, int rawSamples );

  public void waitForInitializationComplete() {
    return;
  }

  public void terminate() {}

  public static AudioCodec getInstance(String codecID) {
    if( codecID.equals( "SPEEX" ) )
      return new SpeexCodec();
    if( codecID.equals( "G711" ) )
      return new G711AudioCodec();
    if( codecID.equals( "NullAudioCodec"))
      return new NullAudioCodec();

    throw new AssertionError("Unknown codec: " + codecID);
  }
}
