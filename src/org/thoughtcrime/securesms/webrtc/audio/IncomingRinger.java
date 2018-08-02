package org.thoughtcrime.securesms.webrtc.audio;


import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.util.ServiceUtil;

import java.io.IOException;

public class IncomingRinger {

  private static final String TAG = IncomingRinger.class.getSimpleName();

  private static final long[] VIBRATE_PATTERN = {0, 1000, 1000};

  private final Context context;
  private final Vibrator vibrator;

  private MediaPlayer player;

  IncomingRinger(Context context) {
    this.context  = context.getApplicationContext();
    this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void start(@Nullable Uri uri, boolean vibrate) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    if (player != null) player.release();
    if (uri != null)    player = createPlayer(uri);

    int ringerMode = audioManager.getRingerMode();

    if (shouldVibrate(context, player, ringerMode, vibrate)) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    }

    if (player != null && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
      try {
        if (!player.isPlaying()) {
          player.prepare();
          player.start();
          Log.i(TAG, "Playing ringtone now...");
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
  }

  public void stop() {
    if (player != null) {
      Log.i(TAG, "Stopping ringer");
      player.release();
      player = null;
    }

    Log.i(TAG, "Cancelling vibrator");
    vibrator.cancel();
  }

  private boolean shouldVibrate(Context context, MediaPlayer player, int ringerMode, boolean vibrate) {
    if (player == null) {
      return true;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return shouldVibrateNew(context, ringerMode, vibrate);
    } else {
      return shouldVibrateOld(context, vibrate);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private boolean shouldVibrateNew(Context context, int ringerMode, boolean vibrate) {
    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    if (vibrator == null || !vibrator.hasVibrator()) {
      return false;
    }

    if (vibrate) {
      return ringerMode != AudioManager.RINGER_MODE_SILENT;
    } else {
      return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
    }
  }

  private boolean shouldVibrateOld(Context context, boolean vibrate) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);
    return vibrate && audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER);
  }

  private MediaPlayer createPlayer(@NonNull Uri ringtoneUri) {
    try {
      MediaPlayer mediaPlayer = new MediaPlayer();

      mediaPlayer.setOnErrorListener(new MediaPlayerErrorListener());
      mediaPlayer.setDataSource(context, ringtoneUri);
      mediaPlayer.setLooping(true);
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);

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
