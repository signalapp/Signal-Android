package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.util.Util;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.concurrent.TimeUnit;

class VoiceNoteProximityManager implements SensorEventListener {

  private static final String TAG = Log.tag(VoiceNoteProximityManager.class);

  private static final float  PROXIMITY_THRESHOLD = 5f;

  private final SimpleExoPlayer           player;
  private final AudioManager              audioManager;
  private final SensorManager             sensorManager;
  private final Sensor                    proximitySensor;
  private final PowerManager.WakeLock     wakeLock;
  private final VoiceNoteQueueDataAdapter queueDataAdapter;

  private long startTime;

  VoiceNoteProximityManager(@NonNull Context context,
                            @NonNull SimpleExoPlayer player,
                            @NonNull VoiceNoteQueueDataAdapter queueDataAdapter)
  {
    this.player           = player;
    this.audioManager     = ServiceUtil.getAudioManager(context);
    this.sensorManager    = ServiceUtil.getSensorManager(context);
    this.proximitySensor  = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    this.queueDataAdapter = queueDataAdapter;

    if (Build.VERSION.SDK_INT >= 21) {
      this.wakeLock = ServiceUtil.getPowerManager(context).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    } else {
      this.wakeLock = null;
    }
  }

  void onPlayerReady() {
    startTime = System.currentTimeMillis();
    sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
  }

  void onPlayerEnded() {
    sensorManager.unregisterListener(this);

    if (wakeLock != null && wakeLock.isHeld() && Build.VERSION.SDK_INT >= 21) {
      wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
    }
  }

  void onPlayerError() {
    onPlayerEnded();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_PROXIMITY || player.getPlaybackState() != Player.STATE_READY) {
      return;
    }

    final int desiredStreamType;
    if (event.values[0] < PROXIMITY_THRESHOLD && event.values[0] != proximitySensor.getMaximumRange()) {
      desiredStreamType = AudioManager.STREAM_VOICE_CALL;
    } else {
      desiredStreamType = AudioManager.STREAM_MUSIC;
    }

    final int currentStreamType = Util.getStreamTypeForAudioUsage(player.getAudioAttributes().usage);

    final long threadId;
    final int  windowIndex = player.getCurrentWindowIndex();

    if (queueDataAdapter.isEmpty() || windowIndex == C.INDEX_UNSET) {
      threadId = -1;
    } else {
      MediaDescriptionCompat mediaDescriptionCompat = queueDataAdapter.getMediaDescription(windowIndex);

      if (mediaDescriptionCompat.getExtras() == null) {
        threadId = -1;
      } else {
        threadId = mediaDescriptionCompat.getExtras().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_THREAD_ID, -1);
      }
    }

    if (desiredStreamType == AudioManager.STREAM_VOICE_CALL &&
        desiredStreamType != currentStreamType              &&
        !audioManager.isWiredHeadsetOn()                    &&
        threadId != -1                                      &&
        ApplicationDependencies.getMessageNotifier().getVisibleThread() == threadId)
    {
      if (wakeLock != null && !wakeLock.isHeld()) {
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(30));
      }

      player.setPlayWhenReady(false);
      player.setAudioAttributes(new AudioAttributes.Builder()
                                                   .setContentType(C.CONTENT_TYPE_SPEECH)
                                                   .setUsage(C.USAGE_VOICE_COMMUNICATION)
                                                   .build());
      player.setPlayWhenReady(true);

      startTime = System.currentTimeMillis();
    } else if (desiredStreamType == AudioManager.STREAM_MUSIC &&
               desiredStreamType != currentStreamType         &&
               System.currentTimeMillis() - startTime > 500)
    {
      if (wakeLock != null) {
        if (wakeLock.isHeld()) {
          wakeLock.release();
        }

        player.setPlayWhenReady(false);
        player.setAudioAttributes(new AudioAttributes.Builder()
                                                     .setContentType(C.CONTENT_TYPE_MUSIC)
                                                     .setUsage(C.USAGE_MEDIA)
                                                     .build(),
                                  true);
      }
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }
}