package org.thoughtcrime.securesms.webrtc.audio;


import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.RingtoneUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.io.IOException;

public class IncomingRinger {

  private static final String TAG = Log.tag(IncomingRinger.class);

  private static final long[] VIBRATE_PATTERN = {0, 1000, 1000};

  private final Context  context;
  private final Vibrator vibrator;

  private MediaPlayer player;

  IncomingRinger(Context context) {
    this.context  = context.getApplicationContext();
    this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  public void start(@Nullable Uri uri, boolean vibrate) {

    if (player != null) {
      player.release();
    }

    if (uri != null) {
      player = createPlayer(uri);
    }

    int ringerMode = getAudioManagerRingMode();

    if (shouldVibrate(context, player, ringerMode, vibrate)) {
      Log.i(TAG, "Starting vibration");
      vibrator.vibrate(VIBRATE_PATTERN, 1);
    } else {
      Log.i(TAG, "Skipping vibration");
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
      Log.w(TAG, "Not ringing, player: " + (player != null ? "available" : "null") + " modeInt: " + ringerMode + " mode: " + (ringerMode == AudioManager.RINGER_MODE_SILENT ? "silent" : "vibrate only"));
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

  /**
   * Overrides the ringer mode if we are on the right API level and have the right policy access.
   * Checks the ringer volume to make sure we're not going to blast someone with their ringtone inadvertently.
   * Safe to do because at this point, we've already checked the policy for the given incoming call peer.
   */
  private int getAudioManagerRingMode() {
    AudioManager        audioManager        = ServiceUtil.getAudioManager(context);
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);
    int                 ringerMode          = audioManager.getRingerMode();

    if (Build.VERSION.SDK_INT >= 28 && !notificationManager.isNotificationPolicyAccessGranted()) {
      int ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);

      if (ringVolume > 0 && ringerMode == AudioManager.RINGER_MODE_SILENT) {
        return AudioManager.RINGER_MODE_NORMAL;
      }
    }

    return ringerMode;
  }

  private boolean shouldVibrate(Context context, MediaPlayer player, int ringerMode, boolean vibrate) {
    if (player == null) {
      return true;
    }

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

  private @Nullable MediaPlayer createPlayer(@NonNull Uri ringtoneUri) {
    try {
      MediaPlayer mediaPlayer = safeCreatePlayer(ringtoneUri);

      if (mediaPlayer == null) {
        Log.w(TAG, "Failed to create player for incoming call ringer due to custom rom most likely");
        return null;
      }

      mediaPlayer.setOnErrorListener(new MediaPlayerErrorListener());
      mediaPlayer.setLooping(true);

      if (Build.VERSION.SDK_INT <= 21) {
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
      } else {
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                                                    .build());
      }

      return mediaPlayer;
    } catch (IOException e) {
      Log.e(TAG, "Failed to create player for incoming call ringer", e);
      return null;
    }
  }

  private @Nullable MediaPlayer safeCreatePlayer(@NonNull Uri ringtoneUri) throws IOException {
    try {
      MediaPlayer mediaPlayer = new MediaPlayer();
      mediaPlayer.setDataSource(context, ringtoneUri);
      return mediaPlayer;
    } catch (IOException | SecurityException e) {
      Log.w(TAG, "Failed to create player with ringtone the normal way", e);
    }

    try {
      Uri defaultRingtoneUri = RingtoneUtil.getActualDefaultRingtoneUri(context);
      if (defaultRingtoneUri != null) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setDataSource(context, defaultRingtoneUri);
        return mediaPlayer;
      }
    } catch (SecurityException e) {
      Log.w(TAG, "Failed to set default ringtone with fallback approach", e);
    }

    return null;
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
