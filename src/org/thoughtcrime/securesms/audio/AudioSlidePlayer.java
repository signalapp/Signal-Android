package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentServer;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class AudioSlidePlayer implements SensorEventListener {

  private static final String TAG = AudioSlidePlayer.class.getSimpleName();

  private static @NonNull Optional<AudioSlidePlayer> playing = Optional.absent();

  private final @NonNull  Context           context;
  private final @NonNull  AudioSlide        slide;
  private final @NonNull  Handler           progressEventHandler;
  private final @NonNull  AudioManager      audioManager;
  private final @NonNull  SensorManager     sensorManager;
  private final @NonNull  Sensor            proximitySensor;
  private final @Nullable WakeLock          wakeLock;

  private @NonNull  WeakReference<Listener> listener;
  private @Nullable MediaPlayerWrapper      mediaPlayer;
  private @Nullable AttachmentServer        audioAttachmentServer;
  private           long                    startTime;

  public synchronized static AudioSlidePlayer createFor(@NonNull Context context,
                                                        @NonNull AudioSlide slide,
                                                        @NonNull Listener listener)
  {
    if (playing.isPresent() && playing.get().getAudioSlide().equals(slide)) {
      playing.get().setListener(listener);
      return playing.get();
    } else {
      return new AudioSlidePlayer(context, slide, listener);
    }
  }

  private AudioSlidePlayer(@NonNull Context context,
                           @NonNull AudioSlide slide,
                           @NonNull Listener listener)
  {
    this.context              = context;
    this.slide                = slide;
    this.listener             = new WeakReference<>(listener);
    this.progressEventHandler = new ProgressEventHandler(this);
    this.audioManager         = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    this.sensorManager        = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
    this.proximitySensor      = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

    if (Build.VERSION.SDK_INT >= 21) {
      this.wakeLock = ServiceUtil.getPowerManager(context).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    } else {
      this.wakeLock = null;
    }
  }

  public void play(final double progress) throws IOException {
    play(progress, false);
  }

  private void play(final double progress, boolean earpiece) throws IOException {
    if (this.mediaPlayer != null) return;

    this.mediaPlayer           = new MediaPlayerWrapper();
    this.audioAttachmentServer = new AttachmentServer(context, slide.asAttachment());
    this.startTime             = System.currentTimeMillis();

    audioAttachmentServer.start();

    mediaPlayer.setDataSource(context, audioAttachmentServer.getUri());
    mediaPlayer.setAudioStreamType(earpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      @Override
      public void onPrepared(MediaPlayer mp) {
        Log.w(TAG, "onPrepared");
        synchronized (AudioSlidePlayer.this) {
          if (mediaPlayer == null) return;

          if (progress > 0) {
            mediaPlayer.seekTo((int) (mediaPlayer.getDuration() * progress));
          }

          sensorManager.registerListener(AudioSlidePlayer.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
          mediaPlayer.start();

          setPlaying(AudioSlidePlayer.this);
        }

        notifyOnStart();
        progressEventHandler.sendEmptyMessage(0);
      }
    });

    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        Log.w(TAG, "onComplete");
        synchronized (AudioSlidePlayer.this) {
          mediaPlayer = null;

          if (audioAttachmentServer != null) {
            audioAttachmentServer.stop();
            audioAttachmentServer = null;
          }

          sensorManager.unregisterListener(AudioSlidePlayer.this);

          if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
          }
        }

        notifyOnStop();
        progressEventHandler.removeMessages(0);
      }
    });

    mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
      @Override
      public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.w(TAG, "MediaPlayer Error: " + what + " , " + extra);

        Toast.makeText(context, R.string.AudioSlidePlayer_error_playing_audio, Toast.LENGTH_SHORT).show();

        synchronized (AudioSlidePlayer.this) {
          mediaPlayer = null;

          if (audioAttachmentServer != null) {
            audioAttachmentServer.stop();
            audioAttachmentServer = null;
          }

          sensorManager.unregisterListener(AudioSlidePlayer.this);

          if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
          }
        }

        notifyOnStop();
        progressEventHandler.removeMessages(0);
        return true;
      }
    });

    mediaPlayer.prepareAsync();
  }

  public synchronized void stop() {
    Log.w(TAG, "Stop called!");

    removePlaying(this);
    progressEventHandler.removeMessages(0);

    if (this.mediaPlayer != null) {
      this.mediaPlayer.stop();
      this.mediaPlayer.release();
    }

    if (this.audioAttachmentServer != null) {
      this.audioAttachmentServer.stop();
    }

    sensorManager.unregisterListener(AudioSlidePlayer.this);

    this.mediaPlayer           = null;
    this.audioAttachmentServer = null;
  }

  public synchronized static void stopAll() {
    if (playing.isPresent()) {
      playing.get().stop();
    }
  }

  public synchronized static boolean stopIfIsPlaying(@Nullable AudioSlide slide) {
    if (playing.isPresent() && playing.get().getAudioSlide().equals(slide)) {
      playing.get().stop();
      return true;
    } else {
      return false;
    }
  }

  public void setListener(@NonNull Listener listener) {
    this.listener = new WeakReference<>(listener);

    if (this.mediaPlayer != null && this.mediaPlayer.isPlaying()) {
      notifyOnStart();
    }
  }

  public @NonNull AudioSlide getAudioSlide() {
    return slide;
  }


  private Pair<Double, Integer> getProgress() {
    if (mediaPlayer == null || mediaPlayer.getCurrentPosition() <= 0 || mediaPlayer.getDuration() <= 0) {
      return new Pair<>(0D, 0);
    } else {
      return new Pair<>((double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration(),
                        mediaPlayer.getCurrentPosition());
    }
  }

  private void notifyOnStart() {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        getListener().onStart();
      }
    });
  }

  private void notifyOnStop() {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        getListener().onStop();
      }
    });
  }

  private void notifyOnProgress(final double progress, final long millis) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        getListener().onProgress(progress, millis);
      }
    });
  }

  private @NonNull Listener getListener() {
    Listener listener = this.listener.get();

    if (listener != null) return listener;
    else                  return new Listener() {
      @Override
      public void onStart() {}
      @Override
      public void onStop() {}
      @Override
      public void onProgress(double progress, long millis) {}
    };
  }

  private synchronized static void setPlaying(@NonNull AudioSlidePlayer player) {
    if (playing.isPresent() && playing.get() != player) {
      playing.get().notifyOnStop();
      playing.get().stop();
    }

    playing = Optional.of(player);
  }

  private synchronized static void removePlaying(@NonNull AudioSlidePlayer player) {
    if (playing.isPresent() && playing.get() == player) {
      playing = Optional.absent();
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) return;
    if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;

    int streamType;

    if (event.values[0] < 5f && event.values[0] != proximitySensor.getMaximumRange()) {
      streamType = AudioManager.STREAM_VOICE_CALL;
    } else {
      streamType = AudioManager.STREAM_MUSIC;
    }

    if (streamType == AudioManager.STREAM_VOICE_CALL &&
        mediaPlayer.getAudioStreamType() != streamType &&
        !audioManager.isWiredHeadsetOn())
    {
      double position = mediaPlayer.getCurrentPosition();
      double duration = mediaPlayer.getDuration();
      double progress = position / duration;

      if (wakeLock != null) wakeLock.acquire();
      stop();
      try {
        play(progress, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    } else if (streamType == AudioManager.STREAM_MUSIC &&
               mediaPlayer.getAudioStreamType() != streamType &&
               System.currentTimeMillis() - startTime > 500)
    {
      if (wakeLock != null) wakeLock.release();
      stop();
      notifyOnStop();
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }

  public interface Listener {
    public void onStart();
    public void onStop();
    public void onProgress(double progress, long millis);
  }

  private static class ProgressEventHandler extends Handler {

    private final WeakReference<AudioSlidePlayer> playerReference;

    private ProgressEventHandler(@NonNull AudioSlidePlayer player) {
      this.playerReference = new WeakReference<>(player);
    }

    @Override
    public void handleMessage(Message msg) {
      AudioSlidePlayer player = playerReference.get();

      if (player == null || player.mediaPlayer == null || !player.mediaPlayer.isPlaying()) {
        return;
      }

      Pair<Double, Integer> progress = player.getProgress();
      player.notifyOnProgress(progress.first, progress.second);
      sendEmptyMessageDelayed(0, 50);
    }
  }

  private static class MediaPlayerWrapper extends MediaPlayer {

    private int streamType;

    @Override
    public void setAudioStreamType(int streamType) {
      this.streamType = streamType;
      super.setAudioStreamType(streamType);
    }

    public int getAudioStreamType() {
      return streamType;
    }
  }
}
