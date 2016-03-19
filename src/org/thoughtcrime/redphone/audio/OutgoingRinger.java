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
  private boolean     loopEnabled;
  private Context     context;

  public OutgoingRinger(Context context) {
    this.context = context;

    loopEnabled = true;
    currentSoundID = -1;

  }

  public void playSonar() {
    start(R.raw.redphone_sonarping);
  }

  public void playHandshake() {
    start(R.raw.redphone_handshake);
  }

  public void playRing() {
    start(R.raw.redphone_outring);
  }

  public void playComplete() {
    stop(R.raw.redphone_completed);
  }

  public void playFailure() {
    stop(R.raw.redphone_failure);
  }

  public void playBusy() {
    start(R.raw.redphone_busy);
  }

  private void setSound( int soundID ) {
    currentSoundID = soundID;
    loopEnabled = true;
  }

  private void start( int soundID ) {
    if( soundID == currentSoundID ) return;
    setSound( soundID );
    start();
  }

  private void start() {
    if( mediaPlayer != null ) mediaPlayer.release();
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
    mediaPlayer.setOnCompletionListener(this);
    mediaPlayer.setOnPreparedListener(this);
    mediaPlayer.setLooping(loopEnabled);

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

  private void stop( int soundID ) {
    setSound( soundID );
    loopEnabled = false;
    start();
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

    try {
      mp.start();
    } catch (IllegalStateException e) {
      Log.w(TAG, e);
    }
  }
}
