package org.thoughtcrime.securesms.service;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;

import java.lang.ref.WeakReference;

import org.thoughtcrime.securesms.logging.Log;

/** An interface that defines how an audio player is interact with the service. */
interface MediaPlayer {
  @C.StreamType int getAudioStreamType();
  int getBufferedPercentage();
  long getCurrentPosition();
  long getDuration();
  int getPlaybackState();
  void release();
  void removeListener(Player.EventListener listener);
  void seekTo(long positionMs);
  void stop();
}

/**
 * An interface that defines how to create a MediaPlayer instance.
 * The primary purpose of this interface is to allow a dependency (ExoPlayerFactory)
 * be injected at runtime so it can make testing easier.
 */
interface MediaPlayerFactory {
  MediaPlayer create(Uri uri, Player.EventListener listener, boolean earpiece);
}

/**
 * An interface that defines how the service interacts with a proximity sensor.
 * The primary purpose of this interface is to allow a dependency (Sensor)
 * be injected at runtime so it can make testing easier.
 */
interface ProximitySensor {
  float getMaximumRange();
  void registerListener(SensorEventListener listener, int samplingPeriodUs);
  void unregisterListener(SensorEventListener listener);
}

/**
 * An interface that defines how the service interacts with the audio manager.
 * The primary purpose of this interface is to allow a dependency (AudioManager)
 * be injected at runtime so it can make testing easier.
 */
interface AudioManager {
  boolean isWiredHeadsetOn();
}

/**
 * An interface that makes it easy to mock WakeLock object.
 * The primary purpose of this interface is to allow a dependency (WakeLock)
 * be injected at runtime so it can make testing easier.
 */
interface WakeLock {
  void acquire();
  void release();
  void releaseWaitForNoProximity();
  boolean isHeld();
}

/**
 * An interface that defines how the real world android.app.Service is called back from
 * the AudioPlayerServiceBackend object.
 * The primary purpose of this interface is to allow a dependency (Service)
 * be injected at runtime so it can make testing easier.
 */
interface ServiceInterface {
  void startForeground(AudioPlayerServiceBackend.Command command);
  void updateNotification(AudioPlayerServiceBackend.Command command);
  void stopDelayed();
  void clearDelayedStop();
  void stop();
}

interface Clock {
  long currentTimeMillis();
}

/**
 * Testable backend of an android.app.Service that plays back an audio in the foreground service.
 */
public class AudioPlayerServiceBackend {
  private static final String TAG             = AudioPlayerServiceBackend.class.getSimpleName();
  public  static final String MEDIA_URI_EXTRA = "AudioPlayerService_media_uri_extra";
  public  static final String PROGRESS_EXTRA  = "AudioPlayerService_progress_extra";
  public  static final String COMMAND_EXTRA   = "AudioPlayerService_command_extra";

  public enum Command {
    UNKNOWN, PLAY, PAUSE, RESUME, CLOSE
  }

  private final AudioManager         audioManager;
  private final Clock                clock;
  private final ServiceInterface     serviceInterface;
  private final LocalBinder          binder = new LocalBinder();
  private final MediaPlayerFactory   mediaPlayerFactory;
  private final ProgressEventHandler progressEventHandler = new ProgressEventHandler(this);
  private final ProximitySensor      proximitySensor;

  private @Nullable final WakeLock wakeLock;

  private boolean               earpiece;
  private @Nullable MediaPlayer mediaPlayer;
  private @Nullable Uri         mediaUri;
  private double                progress;
  private long                  startTime;

  /** Constructor. Call from the service's onCreate(). */
  AudioPlayerServiceBackend(AudioManager audioManager, Clock clock, MediaPlayerFactory mediaPlayerFactory,
      ProximitySensor proximitySensor, ServiceInterface serviceInterface, @Nullable WakeLock wakeLock) {
    this.audioManager       = audioManager;
    this.clock              = clock;
    this.mediaPlayerFactory = mediaPlayerFactory;
    this.proximitySensor    = proximitySensor;
    this.serviceInterface   = serviceInterface;
    this.wakeLock           = wakeLock;

    proximitySensor.registerListener(sensorEventListener, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
  }

  /** Call from the service's onDestroy() */
  void onDestroy() {
    cleanUp();
  }

  /** Call from the service's onStartCommand. */
  void onStartCommand(Intent intent) {
    handleCommand(intent);
  }

  /** Call from the service's onBind. */
  IBinder onBind(Intent intent) {
    return binder;
  }

  /** Call from the service's onUnbind. */
  boolean onUnbind(Intent intent) {
    // returns true because clients can rebind to this service
    return true;
  }

  /**
   * A SensorEventListener that listens to a proximity sensor and changes audio output according to
   * how close the person is to the phone.
   */
  private final SensorEventListener sensorEventListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) return;
      if (mediaPlayer == null || mediaPlayer.getPlaybackState() != Player.STATE_READY) return;

      int streamType;

      if (event.values[0] < 5f && event.values[0] != proximitySensor.getMaximumRange()) {
        streamType = android.media.AudioManager.STREAM_VOICE_CALL;
        earpiece = true;
      } else {
        streamType = android.media.AudioManager.STREAM_MUSIC;
        earpiece = false;
      }

      if (streamType == android.media.AudioManager.STREAM_VOICE_CALL &&
          mediaPlayer.getAudioStreamType() != streamType &&
          !audioManager.isWiredHeadsetOn()) {

        if (wakeLock != null) wakeLock.acquire();
        changeStreamType();
      } else if (streamType == android.media.AudioManager.STREAM_MUSIC &&
          mediaPlayer.getAudioStreamType() != streamType &&
          clock.currentTimeMillis() - startTime > 500) {
        if (wakeLock != null) wakeLock.release();
        changeStreamType();
      }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
  };

  /**
   * A listener that handles a few interesting events that happens in a media player
   * during the lifecycle of an audio playback.
   */
  private final Player.EventListener playerEventListener = new Player.EventListener() {
    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      Log.d(TAG, "onPlayerStateChanged(" + playWhenReady + ", " + playbackState + ")");
      switch (playbackState) {
        case Player.STATE_IDLE:
          serviceInterface.stopDelayed();
          break;
        case Player.STATE_BUFFERING:
          serviceInterface.clearDelayedStop();
          break;
        case Player.STATE_READY:
          serviceInterface.clearDelayedStop();
          Log.i(TAG, "onPrepared() " + mediaPlayer.getBufferedPercentage() + "% buffered");
          synchronized (AudioPlayerServiceBackend.this) {
            if (mediaPlayer == null) return;

            if (progress > 0) {
              mediaPlayer.seekTo((long) (mediaPlayer.getDuration() * progress));
            }
          }

          binder.notifyOnStart();
          progressEventHandler.sendEmptyMessage(0);
          break;

        case Player.STATE_ENDED:
          Log.i(TAG, "onComplete");
          synchronized (AudioPlayerServiceBackend.this) {
            if (wakeLock != null && wakeLock.isHeld()) {
              wakeLock.releaseWaitForNoProximity();
            }
          }

          binder.notifyOnStop();
          progressEventHandler.removeMessages(0);
          serviceInterface.stop();
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      Log.w(TAG, "MediaPlayer Error: " + error);

      synchronized (AudioPlayerServiceBackend.this) {
        if (wakeLock != null && wakeLock.isHeld()) {
          wakeLock.releaseWaitForNoProximity();
        }
      }

      binder.notifyOnStop();
      binder.notifyOnError(error);
      progressEventHandler.removeMessages(0);
    }
  };

  /** Call when a "command" is received from outside of the service. */
  private void handleCommand(Intent intent) {
    Command command = (Command) intent.getSerializableExtra(COMMAND_EXTRA);
    switch (command) {
      case PLAY:
        mediaUri = intent.getParcelableExtra(MEDIA_URI_EXTRA);
        progress = intent.getDoubleExtra(PROGRESS_EXTRA, 0);
        serviceInterface.startForeground(command);
        play();
        break;
      case PAUSE:
        pause();
        break;
      case RESUME:
        resume();
        break;
      case CLOSE:
        stopService();
        break;
      default:
        break;
    }
  }

  /** Call to start playback. */
  private void play() {
    if (mediaUri == null) return;
    mediaPlayer = mediaPlayerFactory.create(mediaUri, playerEventListener, earpiece);
    startTime = clock.currentTimeMillis();
  }

  /** Call after the playback is paused. */
  private void resume() {
    play();
    serviceInterface.updateNotification(Command.RESUME);
  }

  /** Call after the playback is playing. */
  private void pause() {
    if (mediaPlayer == null) return;
    progress = getProgress().first;
    mediaPlayer.stop();
    mediaPlayer.release();
    mediaPlayer = null;
    binder.notifyOnStop();
    serviceInterface.updateNotification(Command.PAUSE);
  }

  /** Call when the mediaPlayer must change the stream type i.e. where to output audio. */
  private void changeStreamType() {
    if (mediaPlayer == null) return;
    progress = getProgress().first;
    mediaPlayer.stop();
    mediaPlayer.release();
    play();
  }

  /** Call when the service should end. */
  private void stopService() {
    pause();
    mediaUri = null;
    progress = 0;
    earpiece = false;
    serviceInterface.stop();
  }

  /** Call when the service is about to be destroyed */
  private void cleanUp() {
    proximitySensor.unregisterListener(sensorEventListener);
    if (mediaPlayer != null) {
      mediaPlayer.removeListener(playerEventListener);
      mediaPlayer.stop();
      mediaPlayer.release();
    }
    mediaPlayer = null;
  }

  private Pair<Double, Integer> getProgress() {
    if (mediaPlayer == null || mediaPlayer.getCurrentPosition() <= 0 || mediaPlayer.getDuration() <= 0) {
      return new Pair<>(0D, 0);
    } else {
      return new Pair<>((double) mediaPlayer.getCurrentPosition() / (double) mediaPlayer.getDuration(),
          (int) mediaPlayer.getCurrentPosition());
    }
  }

  public class LocalBinder extends Binder {
    private AudioStateListener listener;

    public void stop() {
      AudioPlayerServiceBackend.this.pause();
    }

    private void notifyOnStart() {
      if (listener == null) return;
      listener.onAudioStarted();
    }

    private void notifyOnStop() {
      if (listener == null) return;
      listener.onAudioStopped();
    }

    private void notifyOnError(ExoPlaybackException error) {
      if (listener == null) return;
      listener.onAudioError(error);
    }

    private void notifyOnProgress(final double progress, final long millis) {
      if (listener == null) return;
      listener.onAudioProgress(progress, millis);
    }

    public void setListener(AudioStateListener listener) {
      this.listener = listener;
    }
  }

  private static class ProgressEventHandler extends Handler {

    private final WeakReference<AudioPlayerServiceBackend> playerReference;

    private ProgressEventHandler(@NonNull AudioPlayerServiceBackend player) {
      this.playerReference = new WeakReference<>(player);
    }

    @Override
    public void handleMessage(Message msg) {
      AudioPlayerServiceBackend player = playerReference.get();

      if (player == null || player.mediaPlayer == null || !isPlayerActive(player.mediaPlayer)) {
        return;
      }

      Pair<Double, Integer> progress = player.getProgress();
      player.binder.notifyOnProgress(progress.first, progress.second);
      sendEmptyMessageDelayed(0, 50);
    }

    private boolean isPlayerActive(@NonNull MediaPlayer player) {
      return player.getPlaybackState() == Player.STATE_READY || player.getPlaybackState() == Player.STATE_BUFFERING;
    }
  }

  public interface AudioStateListener {
    void onAudioStarted();
    void onAudioStopped();
    void onAudioError(final ExoPlaybackException error);
    void onAudioProgress(final double progress, final long millis);
  }
}
