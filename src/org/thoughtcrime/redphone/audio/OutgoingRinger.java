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

package org.thoughtcrime.redphone.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.io.IOException;

/**
 * Handles loading and playing the sequence of sounds we use to indicate call initialization.
 *
 * @author Stuart O. Anderson
 */
public class OutgoingRinger implements MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

  private static final String TAG = OutgoingRinger.class.getSimpleName();

  private MediaPlayer mediaPlayer;
  private int         currentSoundID;
  private Context     context;

  public OutgoingRinger(Context context) {
    this.context = context;
  }

  public void playSonar() {
    start(R.raw.redphone_sonarping, true);
  }

  public void playHandshake() {
    start(R.raw.redphone_handshake, true);
  }

  public void playRing() {
    start(R.raw.redphone_outring, true);
  }

  public void playComplete() {
    start(R.raw.redphone_completed, false);
  }

  public void playFailure() {
    start(R.raw.redphone_failure, false);
  }

  public void playBusy() {
    start(R.raw.redphone_busy, true);
  }

  private void start(int soundID, boolean loop) {
    if( soundID == currentSoundID ) return;

    if (mediaPlayer != null) mediaPlayer.release();

    currentSoundID = soundID;

    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
    mediaPlayer.setOnCompletionListener(this);
    mediaPlayer.setOnPreparedListener(this);
    mediaPlayer.setLooping(loop);

    String packageName = context.getPackageName();
    Uri dataUri = Uri.parse("android.resource://" + packageName + "/" + currentSoundID);

    try {
      mediaPlayer.setDataSource(context, dataUri);
      mediaPlayer.prepareAsync();
    } catch (IllegalArgumentException | SecurityException | IllegalStateException | IOException e) {
      Log.w(TAG, e);
      // TODO Auto-generated catch block
      return;
    }
  }

  public void stop() {
    if (mediaPlayer == null) return;
    mediaPlayer.release();
    mediaPlayer = null;

    currentSoundID = -1;
  }

  public void onCompletion(MediaPlayer mp) {
    //mediaPlayer.release();
    //mediaPlayer = null;
  }

  public void onPrepared(MediaPlayer mp) {
    AudioManager am = ServiceUtil.getAudioManager(context);

    if (am.isBluetoothScoAvailableOffCall()) {
      Log.d(TAG, "bluetooth sco is available");
      try {
        am.startBluetoothSco();
      } catch (NullPointerException e) {
        // Lollipop bug (https://stackoverflow.com/questions/26642218/audiomanager-startbluetoothsco-crashes-on-android-lollipop)
      }
    }

    mp.start();
  }
}
