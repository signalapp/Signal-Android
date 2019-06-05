package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Pair;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentServer;
import org.thoughtcrime.securesms.logging.Log;
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
  private @Nullable SimpleExoPlayer         mediaPlayer;
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

    LoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE).createDefaultLoadControl();
    this.mediaPlayer           = ExoPlayerFactory.newSimpleInstance(context, new DefaultRenderersFactory(context), new DefaultTrackSelector(), loadControl);
    this.audioAttachmentServer = new AttachmentServer(context, slide.asAttachment());
    this.startTime             = System.currentTimeMillis();

    audioAttachmentServer.start();

    mediaPlayer.prepare(createMediaSource(audioAttachmentServer.getUri()));
    mediaPlayer.setPlayWhenReady(true);
    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                                      .setContentType(earpiece ? C.CONTENT_TYPE_SPEECH : C.CONTENT_TYPE_MUSIC)
                                                      .setUsage(earpiece ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                                                      .build());
    mediaPlayer.addListener(new Player.EventListener() {

      boolean started = false;

      @Override
      public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "onPlayerStateChanged(" + playWhenReady + ", " + playbackState + ")");
        switch (playbackState) {
          case Player.STATE_READY:
            Log.i(TAG, "onPrepared() " + mediaPlayer.getBufferedPercentage() + "% buffered");
            synchronized (AudioSlidePlayer.this) {
              if (mediaPlayer == null) return;

              if (started) {
                Log.d(TAG, "Already started. Ignoring.");
                return;
              }

              started = true;

              if (progress > 0) {
                mediaPlayer.seekTo((long) (mediaPlayer.getDuration() * progress));
              }

              sensorManager.registerListener(AudioSlidePlayer.this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);

              setPlaying(AudioSlidePlayer.this);
            }

            notifyOnStart();
            progressEventHandler.sendEmptyMessage(0);
            break;

          case Player.STATE_ENDED:
            Log.i(TAG, "onComplete");
            synchronized (AudioSlidePlayer.this) {
              mediaPlayer = null;

              if (audioAttachmentServer != null) {
                audioAttachmentServer.stop();
                audioAttachmentServer = null;
              }

              sensorManager.unregisterListener(AudioSlidePlayer.this);

              if (wakeLock != null && wakeLock.isHeld()) {
                if (Build.VERSION.SDK_INT >= 21) {
                  wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                }
              }
            }

            notifyOnStop();
            progressEventHandler.removeMessages(0);
        }
      }

      @Override
      public void onPlayerError(ExoPlaybackException error) {
        Log.w(TAG, "MediaPlayer Error: " + error);

        Toast.makeText(context, R.string.AudioSlidePlayer_error_playing_audio, Toast.LENGTH_SHORT).show();

        synchronized (AudioSlidePlayer.this) {
          mediaPlayer = null;

          if (audioAttachmentServer != null) {
            audioAttachmentServer.stop();
            audioAttachmentServer = null;
          }

          sensorManager.unregisterListener(AudioSlidePlayer.this);

          if (wakeLock != null && wakeLock.isHeld()) {
            if (Build.VERSION.SDK_INT >= 21) {
              wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            }
          }
        }

        notifyOnStop();
        progressEventHandler.removeMessages(0);
      }
    });
  }

  private MediaSource createMediaSource(@NonNull Uri uri) {
    return new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, BuildConfig.USER_AGENT))
                                   .setExtractorsFactory(new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true))
                                   .createMediaSource(uri);
  }

  public synchronized void stop() {
    Log.i(TAG, "Stop called!");

    removePlaying(this);

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

  public void setListener(@NonNull Listener listener) {
    this.listener = new WeakReference<>(listener);

    if (this.mediaPlayer != null && this.mediaPlayer.getPlaybackState() == Player.STATE_READY) {
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
                        (int) mediaPlayer.getCurrentPosition());
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
    if (mediaPlayer == null || mediaPlayer.getPlaybackState() != Player.STATE_READY) return;

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
    void onStart();
    void onStop();
    void onProgress(double progress, long millis);
  }

  private static class ProgressEventHandler extends Handler {

    private final WeakReference<AudioSlidePlayer> playerReference;

    private ProgressEventHandler(@NonNull AudioSlidePlayer player) {
      this.playerReference = new WeakReference<>(player);
    }

    @Override
    public void handleMessage(Message msg) {
      AudioSlidePlayer player = playerReference.get();

      if (player == null || player.mediaPlayer == null || !isPlayerActive(player.mediaPlayer)) {
        return;
      }

      Pair<Double, Integer> progress = player.getProgress();
      player.notifyOnProgress(progress.first, progress.second);
      sendEmptyMessageDelayed(0, 50);
    }

    private boolean isPlayerActive(@NonNull SimpleExoPlayer player) {
      return player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING;
    }
  }
}
