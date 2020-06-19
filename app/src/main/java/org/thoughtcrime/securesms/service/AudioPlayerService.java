package org.thoughtcrime.securesms.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend.Command;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.video.exo.AttachmentDataSourceFactory;

public class AudioPlayerService extends Service {

  private static final String TAG = AudioPlayerService.class.getSimpleName();

  private AudioPlayerServiceBackend backend;

  @Override
  public void onCreate() {
    super.onCreate();
    AudioManager audioManager   = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

    WakeLock wakeLock;
    if (Build.VERSION.SDK_INT >= 21) {
      wakeLock = ServiceUtil.getPowerManager(this).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
    } else {
      wakeLock = null;
    }

    backend = new AudioPlayerServiceBackend(
        new RealAudioManager(audioManager),
        new RealClock(),
        new RealMediaPlayerFactory(this),
        new RealProximitySensor(sensorManager),
        new RealServiceInterface(this),
        new RealWakeLock(wakeLock)
    );
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    backend.onDestroy();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    backend.onStartCommand(intent);
    return Service.START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return backend.onBind(intent);
  }

  @Override
  public boolean onUnbind(Intent intent) {
    return backend.onUnbind(intent);
  }

  /**
   * RealServiceInterface implements a physical implementation of ServiceInterface
   * to which AudioPlayerServiceBackend calls back to execute something that involves
   * user interface (Notification) and service life cycle.
   */
  static class RealServiceInterface implements ServiceInterface {
    private static final int FOREGROUND_ID = 313499;
    private static final int IDLE_STOP_MS  = 60 * 1000;

    private final Service service;

    RealServiceInterface(Service service) {
      this.service = service;
    }

    private final Handler stopTimerHandler = new Handler();
    private final Runnable stopSelfRunnable = new Runnable() {
      @Override
      public void run() {
        service.stopSelf();
      }
    };

    @Override
    public void startForeground(Command command) {
      service.startForeground(FOREGROUND_ID, createNotification(command));
    }

    @Override
    public void updateNotification(Command command) {
      NotificationManagerCompat.from(service).notify(FOREGROUND_ID, createNotification(command));
    }

    @Override
    public void stopDelayed() {
      stopTimerHandler.postDelayed(stopSelfRunnable, IDLE_STOP_MS);
    }

    @Override
    public void clearDelayedStop() {
      stopTimerHandler.removeCallbacks(stopSelfRunnable);
    }

    @Override
    public void stop() {
      service.stopSelf();
    }

    private Notification createNotification(Command command) {
      NotificationCompat.Builder builder = new NotificationCompat.Builder(service, NotificationChannels.OTHER);
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.ic_signal_grey_24dp);

      addActionsTo(builder, command);

      return builder.build();
    }

    private void addActionsTo(NotificationCompat.Builder builder, Command command) {
      Intent closeIntent = new Intent(service, AudioPlayerService.class);
      closeIntent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, Command.CLOSE);
      PendingIntent piClose = PendingIntent.getService(service, Command.CLOSE.ordinal(), closeIntent, 0);
      switch (command) {
        case PLAY:
        case RESUME:
          builder.setContentTitle(service.getString(R.string.AudioPlayerService_notification_title));
          builder.setContentText(service.getString(R.string.AudioPlayerService_notification_message));
          Intent pauseIntent = new Intent(service, AudioPlayerService.class);
          pauseIntent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, Command.PAUSE);
          PendingIntent piPause = PendingIntent.getService(service, Command.PAUSE.ordinal(), pauseIntent, 0);
          builder.addAction(0, service.getString(R.string.AudioPlayerService_action_pause), piPause);
          builder.addAction(0, service.getString(R.string.AudioPlayerService_action_close), piClose);
          break;
        case PAUSE:
          builder.setContentTitle(service.getString(R.string.AudioPlayerService_notification_title));
          builder.setContentText(service.getString(R.string.AudioPlayerService_notification_message));
          Intent resumeIntent = new Intent(service, AudioPlayerService.class);
          resumeIntent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, Command.RESUME);
          PendingIntent piResume = PendingIntent.getService(service, Command.RESUME.ordinal(), resumeIntent, 0);
          builder.addAction(0, service.getString(R.string.AudioPlayerService_action_resume), piResume);
          builder.addAction(0, service.getString(R.string.AudioPlayerService_action_close), piClose);
          break;
        case CLOSE:
          builder.setContentTitle(service.getString(R.string.AudioPlayerService_notification_title_finished));
          builder.setContentText(service.getString(R.string.AudioPlayerService_notification_message_finished));
        default:
          break;
      }
    }
  }

  /**
   * RealMediaPlayer implements MediaPlayer that represents the interaction between
   * the foreground service and SimpleExoPlayer.
   */
  static class RealMediaPlayer implements MediaPlayer {
    private final SimpleExoPlayer mediaPlayer;
    RealMediaPlayer(SimpleExoPlayer mediaPlayer) {
      this.mediaPlayer = mediaPlayer;
    }

    @Override
    public int getAudioStreamType() {
      return mediaPlayer.getAudioStreamType();
    }

    @Override
    public int getBufferedPercentage() {
      return mediaPlayer.getBufferedPercentage();
    }

    @Override
    public long getCurrentPosition() {
      return mediaPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
      return mediaPlayer.getDuration();
    }

    @Override
    public int getPlaybackState() {
      return mediaPlayer.getPlaybackState();
    }

    @Override
    public void release() {
      mediaPlayer.release();
    }

    @Override
    public void removeListener(EventListener listener) {
      mediaPlayer.removeListener(listener);
    }

    @Override
    public void seekTo(long positionMs) {
      mediaPlayer.seekTo(positionMs);
    }

    @Override
    public void stop() {
      mediaPlayer.stop();
    }
  }

  /**
   * RealMediaPlayerFactory implements MediaPlayerFactory which AudioPlayerServiceBackend uses
   * to create a new MediaPlayer instance.
   */
  static class RealMediaPlayerFactory implements MediaPlayerFactory {
    private final Context context;
    RealMediaPlayerFactory(Context context) {
      this.context = context;
    }

    @Override
    public MediaPlayer create(Uri uri, EventListener listener, boolean earpiece) {
      LoadControl loadControl = new DefaultLoadControl
          .Builder()
          .setBufferDurationsMs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
          .createDefaultLoadControl();
      SimpleExoPlayer mediaPlayer = ExoPlayerFactory
          .newSimpleInstance(context, new DefaultTrackSelector(), loadControl);
      mediaPlayer.addListener(listener);
      mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
          .setContentType(earpiece ? C.CONTENT_TYPE_SPEECH : C.CONTENT_TYPE_MUSIC)
          .setUsage(earpiece ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
          .build());

      DefaultDataSourceFactory defaultDataSourceFactory =
          new DefaultDataSourceFactory(context, "GenericUserAgent", null);
      AttachmentDataSourceFactory attachmentDataSourceFactory =
          new AttachmentDataSourceFactory(context, defaultDataSourceFactory, null);
      ExtractorsFactory extractorsFactory =
          new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
      ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(attachmentDataSourceFactory)
          .setExtractorsFactory(extractorsFactory)
          .createMediaSource(uri);
      mediaPlayer.prepare(mediaSource);
      mediaPlayer.setPlayWhenReady(true);
      return new RealMediaPlayer(mediaPlayer);
    }
  }

  static class RealAudioManager implements org.thoughtcrime.securesms.service.AudioManager {
    private final AudioManager audioManager;
    RealAudioManager(AudioManager audioManager) {
      this.audioManager = audioManager;
    }

    @Override public boolean isWiredHeadsetOn() {
      return audioManager.isWiredHeadsetOn();
    }
  }

  static class RealProximitySensor implements ProximitySensor {
    private final SensorManager sensorManager;
    private final Sensor sensor;
    RealProximitySensor(SensorManager sensorManager) {
      this.sensorManager = sensorManager;
      this.sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    @Override
    public float getMaximumRange() {
      return sensor.getMaximumRange();
    }

    @Override
    public void registerListener(SensorEventListener listener, int samplingPeriodUs) {
      sensorManager.registerListener(listener, sensor, samplingPeriodUs);
    }

    @Override
    public void unregisterListener(SensorEventListener listener) {
      sensorManager.unregisterListener(listener);
    }
  }

  static class RealWakeLock implements org.thoughtcrime.securesms.service.WakeLock {
    private final WakeLock wakeLock;
    RealWakeLock(WakeLock wakeLock) {
      this.wakeLock = wakeLock;
    }

    @Override
    public void acquire() {
      wakeLock.acquire();
    }

    @Override
    public void release() {
      wakeLock.release();
    }

    @Override
    public void releaseWaitForNoProximity() {
      if (Build.VERSION.SDK_INT >= 21) {
        wakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
      }
    }

    @Override
    public boolean isHeld() {
      return wakeLock.isHeld();
    }
  }

  private static final class RealClock implements Clock {

    @Override public long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  }
}
