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

package org.privatechats.redphone.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

import org.privatechats.securesms.util.ServiceUtil;

import java.io.IOException;

/**
* Plays the 'incoming call' ringtone and manages the audio player state associated with this
* process.
*
* @author Stuart O. Anderson
*/
public class IncomingRinger {
  private static final String TAG = IncomingRinger.class.getName();
  private static final long[] VIBRATE_PATTERN = {0, 1000, 1000};
  private final Context context;
  private final Vibrator vibrator;
  private MediaPlayer player;
  private final MediaPlayer.OnErrorListener playerErrorListener =
      new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
          player = null;
          return false;
        }
      };

  public IncomingRinger(Context context) {
    this.context = context.getApplicationContext();
    vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
    player = createPlayer();
  }

  private MediaPlayer createPlayer() {
    MediaPlayer newPlayer = new MediaPlayer();
    try {
      Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
      newPlayer.setOnErrorListener(playerErrorListener);
      newPlayer.setDataSource(context, ringtoneUri);
      newPlayer.setLooping(true);
      newPlayer.setAudioStreamType(AudioManager.STREAM_RING);
      return newPlayer;
    } catch (IOException e) {
      Log.e(TAG, "Failed to create player for incoming call ringer");
      return null;
    }
  }

  public void start() {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    if(player == null) {
      //retry player creation to pick up changed ringtones or audio server restarts
      player = createPlayer();
    }

    int ringerMode = audioManager.getRingerMode();

    if (shouldVibrate()) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    }

    if (player != null && ringerMode == AudioManager.RINGER_MODE_NORMAL ) {
      Log.d(TAG, "set MODE_RINGTONE audio mode");
      audioManager.setMode(AudioManager.MODE_RINGTONE);
      try {
        if(!player.isPlaying()) {
          player.prepare();
          player.start();
          Log.d(TAG, "Playing ringtone now...");
        } else {
          Log.d(TAG, "Ringtone is already playing, declining to restart.");
        }
      } catch (IllegalStateException | IOException e) {
        Log.w(TAG, e);
        player = null;
      }
    } else {
      Log.d(TAG, " mode: " + ringerMode);
    }
  }

  public void stop() {
    if (player != null) {
      Log.d(TAG, "Stopping ringer");
      player.stop();
    }
    Log.d(TAG, "Cancelling vibrator");
    vibrator.cancel();

    Log.d(TAG, "reset audio mode");
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    audioManager.setMode(AudioManager.MODE_NORMAL);
  }

  private boolean shouldVibrate() {
    if(player == null) {
      return true;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return shouldVibrateNew(context);
    }
    return shouldVibrateOld(context);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private boolean shouldVibrateNew(Context context) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    if (vibrator == null || !vibrator.hasVibrator()) {
      return false;
    }

    boolean vibrateWhenRinging = Settings.System.getInt(context.getContentResolver(), "vibrate_when_ringing", 0) != 0;
    int ringerMode = audioManager.getRingerMode();
    if (vibrateWhenRinging) {
      return ringerMode != AudioManager.RINGER_MODE_SILENT;
    } else {
      return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
    }
  }

  private boolean shouldVibrateOld(Context context) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    return audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER);
  }
}
