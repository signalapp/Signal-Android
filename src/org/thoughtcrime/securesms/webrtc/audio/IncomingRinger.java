package org.thoughtcrime.securesms.webrtc.audio;


import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.util.ServiceUtil;
import org.whispersystems.libsignal.util.guava.Optional;

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

  public void start(@NonNull Optional<Uri> ringtone) {
    AudioManager audioManager = ServiceUtil.getAudioManager(context);

    if (player != null) {
      player.release();
      player = null;
    }
    if (ringtone.isPresent()) {
      final Uri uri = ringtone.get();
      player = createPlayer(uri);
    } else {
      Log.w(TAG, "No ringtone present");
    }

    int ringerMode = audioManager.getRingerMode();

    if (shouldVibrate(context, player, ringerMode)) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    }

    final boolean ringerModeNormal = (ringerMode == AudioManager.RINGER_MODE_NORMAL);
    if (player != null && ringerModeNormal) {
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
    } else if (!ringerModeNormal) {
      Log.w(TAG, "Not ringing, mode: " + ringerMode);
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
