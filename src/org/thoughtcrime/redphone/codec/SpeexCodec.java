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

import android.util.Log;

/**
 * An audio codec that uses the Speex library to encode packets.
 * Calls through to the native library implementations of encode and decode.
 *
 * @author Stuat O. Anderson
 */
public class SpeexCodec extends AudioCodec {
  public static final String TAG = SpeexCodec.class.getSimpleName();
  public Thread loadThread = new Thread() {
    @Override
    public void run() {
      try {
        System.loadLibrary("redspeex");
      } catch (Throwable e) {
        throw new AssertionError(e);
      }
      Log.d(TAG, "loaded redspeex, now opening it");
      int result = openSpeex();
      if (result != 0 ) {
        throw new AssertionError("Speex initialization failed");
      }
    }
  };

  @Override
  public void waitForInitializationComplete() {
    if( loadThread.isAlive() ) {
      Log.d(TAG, "Waiting for Speex to load...");
      try {
        loadThread.join();
      } catch (InterruptedException e) {
        Log.w(TAG, e);
      }
    }
  }

  public SpeexCodec() {
    loadThread.start();
  }

  public native int openSpeex();

  public native void closeSpeex();

  @Override
  public void terminate() {
    closeSpeex();
  }

  @Override
  public native int decode(byte[] encodedData, short[] rawData, int encLen);

  @Override
  public native int encode(short[] rawData, byte[] encodedData, int rawLen);
}
