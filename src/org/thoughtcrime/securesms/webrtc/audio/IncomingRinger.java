package org.thoughtcrime.securesms.webrtc.audio;


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

import org.thoughtcrime.securesms.util.ServiceUtil;

import java.io.IOException;

public class IncomingRinger {

  private static final String TAG = IncomingRinger.class.getSimpleName();

  private static final long[] VIBRATE_PATTERN = {0, 1000, 1000};

  private final Context context;
  private final Vibrator vibrator;

  private MediaPlayer player;

  public IncomingRinger(Context context) {
    this.context  = context.getApplicationContext();
    this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void start(boolean speakerphone) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    if (player != null) player.release();
    player = createPlayer();

    int ringerMode = audioManager.getRingerMode();

    if (shouldVibrate(context, player, ringerMode)) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    }

    if (player != null && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
      try {
        if (!player.isPlaying()) {
          player.prepare();
          player.start();
          Log.w(TAG, "Playing ringtone now...");
        } else {
          Log.w(TAG, "Ringtone is already playing, declining to restart.");
        }
      } catch (IllegalStateException | IOException e) {
        Log.w(TAG, e);
        player = null;
      }
    } else {
      Log.w(TAG, "Not ringing, mode: " + ringerMode);
    }

    if (speakerphone) {
      audioManager.setSpeakerphoneOn(true);
    }
  }

  public void stop() {
    if (player != null) {
      Log.w(TAG, "Stopping ringer");
      player.release();
      player = null;
    }

    Log.w(TAG, "Cancelling vibrator");
    vibrator.cancel();
  }

  private boolean shouldVibrate(Context context, MediaPlayer player, int ringerMode) {
    if (player == null) {
      return true;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return shouldVibrateNew(context, ringerMode);
    } else {
      return shouldVibrateOld(context);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private boolean shouldVibrateNew(Context context, int ringerMode) {
    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    if (vibrator == null || !vibrator.hasVibrator()) {
      return false;
    }

    boolean vibrateWhenRinging = Settings.System.getInt(context.getContentResolver(), "vibrate_when_ringing", 0) != 0;

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

  private MediaPlayer createPlayer() {
    try {
      MediaPlayer mediaPlayer = new MediaPlayer();
      Uri         ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

      mediaPlayer.setOnErrorListener(new MediaPlayerErrorListener());
      mediaPlayer.setDataSource(context, ringtoneUri);
      mediaPlayer.setLooping(true);
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);

      return mediaPlayer;
    } catch (IOException e) {
      Log.e(TAG, "Failed to create player for incoming call ringer");
      return null;
    }
  }


  private class MediaPlayerErrorListener implements MediaPlayer.OnErrorListener {
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
      Log.w(TAG, "onError(" + mp + ", " + what + ", " + extra);
      player = null;
      return false;
    }
  }

}
