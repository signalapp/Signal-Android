package org.thoughtcrime.securesms.service;

import static com.ibm.icu.impl.Assert.fail;
import static org.jsoup.helper.Validate.isTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.Uri;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend.AudioStateListener;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend.Command;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class AudioPlayerServiceBackendTest {
  private final FakeProximitySensor       proximitySensor = new FakeProximitySensor();
  private final FakeWakeLock              wakeLock        = new FakeWakeLock();
  private final FakeClock                 clock           = new FakeClock(0L);
  private @Mock AudioManager              audioManager;
  private @Mock MediaPlayer               mediaPlayer;
  private @Mock MediaPlayerFactory        mediaPlayerFactory;
  private @Mock ServiceInterface          serviceInterface;
  private       AudioPlayerServiceBackend backend;
  private       Player.EventListener      playerEventListener;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(mediaPlayerFactory.create(anyObject(), anyObject(), anyBoolean())).thenReturn(mediaPlayer);
    backend = new AudioPlayerServiceBackend(audioManager, clock, mediaPlayerFactory, proximitySensor, serviceInterface,
        wakeLock);
  }

  private AudioPlayerServiceBackend.LocalBinder setupBound(Uri uri, double progress, AudioStateListener listener) {
    ArgumentCaptor<Player.EventListener> captor = ArgumentCaptor.forClass(Player.EventListener.class);
    when(mediaPlayerFactory.create(anyObject(), captor.capture(), anyBoolean())).thenReturn(mediaPlayer);
    Intent play = playCommand(uri, progress);
    backend.onStartCommand(play);
    AudioPlayerServiceBackend.LocalBinder binder = (AudioPlayerServiceBackend.LocalBinder) backend.onBind(play);
    binder.setListener(listener);
    playerEventListener = captor.getValue();

    return binder;
  }

  @Test
  public void playCommandStartsServiceAndCreateMediaPlayer() {
    Uri uri = Uri.parse("content://1");
    backend.onStartCommand(playCommand(uri, 0));
    verify(serviceInterface).startForeground(Command.PLAY);
    verify(mediaPlayerFactory).create(eq(uri), any(), eq(false));
  }

  @Test
  public void pauseCommandStopsAndReleaseMediaPlayer() {
    Uri uri = Uri.parse("content://2");
    backend.onStartCommand(playCommand(uri, 0));

    backend.onStartCommand(pauseCommand());
    verify(mediaPlayer).getCurrentPosition();
    verify(mediaPlayer).stop();
    verify(mediaPlayer).release();
    verify(serviceInterface).updateNotification(Command.PAUSE);
  }

  @Test
  public void resumeCommandCreatesMediaPlayer() {
    Uri uri = Uri.parse("content://2");
    backend.onStartCommand(playCommand(uri, 0));
    verify(mediaPlayerFactory).create(eq(uri), any(), eq(false));
    backend.onStartCommand(pauseCommand());

    backend.onStartCommand(resumeCommand());
    verify(mediaPlayerFactory, times(2)).create(eq(uri), any(), eq(false));
    verify(serviceInterface).updateNotification(Command.RESUME);
  }

  @Test
  public void externalListenerGetsNotifiedWhenPlayerIsReady() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    verify(mockListener).onAudioStarted();
    verify(mockListener, never()).onAudioError(anyObject());
    verify(mockListener, never()).onAudioStopped();
  }

  @Test
  public void externalListenerGetsNotifiedWhenPlaybackReachesAtEnd() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    // Simulate playback ends
    playerEventListener.onPlayerStateChanged(true, Player.STATE_ENDED);

    verify(mockListener).onAudioStopped();
  }

  @Test
  public void externalListenerGetsNotifiedWhenPlaybackFailed() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback error
    ExoPlaybackException e = ExoPlaybackException.createForSource(new IOException());
    playerEventListener.onPlayerError(e);

    verify(mockListener).onAudioStopped();
    verify(mockListener).onAudioError(e);
  }

  @Test
  public void serviceStopsWhenPlaybackReachesAtEnd() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    // Simulate playback ends
    playerEventListener.onPlayerStateChanged(true, Player.STATE_ENDED);

    verify(serviceInterface).stop();
  }

  @Test
  public void mediaPlayerStartsAtSpecifiedProgress() {
    Uri uri = Uri.parse("content://3");
    double progress = 0.45;
    long duration = 9876L;
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, progress, mockListener);
    when(mediaPlayer.getDuration()).thenReturn(duration);

    // Simulate player becomes ready
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    verify(mediaPlayer).seekTo((long) (duration * progress));
  }

  @Test
  public void delayedStopTimerStartsWhenPlaybackStoppedFromExternal() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    binder.stop();

    verify(serviceInterface).stopDelayed();
  }

  @Test
  public void resumingPlaybackStartsFromTheLastProgress() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    // Mock progress
    when(mediaPlayer.getDuration()).thenReturn(20000L);
    when(mediaPlayer.getCurrentPosition()).thenReturn(1234L);
    binder.stop();

    backend.onStartCommand(resumeCommand());
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    verify(mediaPlayer).seekTo(1234);
  }

  @Test
  public void pausingPlaybackFromExternalUpdatesNotification() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    binder.stop();

    verify(serviceInterface).updateNotification(Command.PAUSE);
  }

  @Test
  public void pausingPlaybackFromNotificationUpdatesNotification() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    backend.onStartCommand(pauseCommand());

    verify(serviceInterface).updateNotification(Command.PAUSE);
  }

  @Test
  public void resumingPlaybackFromExternalUpdatesNotification() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    binder.stop();
    backend.onStartCommand(playCommand(uri, 12));
    verify(serviceInterface, times(2)).startForeground(Command.PLAY);
  }

  @Test
  public void resumingPlaybackFromNotificationUpdatesNotification() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    binder.stop();
    backend.onStartCommand(resumeCommand());
    verify(serviceInterface).updateNotification(Command.RESUME);
  }

  @Test
  public void whenProximityUpdatesItPausesAndRestartsWithUpdatedAudioOutput() {
    Uri uri = Uri.parse("content://4");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_MUSIC);

    // Simulate proximity update
    proximitySensor.listener.onSensorChanged(mockSensorEvent(2f));

    // Simulate playback starts again
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    verify(mediaPlayer).stop();
    verify(mediaPlayer).release();
    // earpiece is now set to true
    verify(mediaPlayerFactory).create(eq(uri), anyObject(), eq(true));
  }

  @Test
  public void whenProximityUpdatesButNotBelowThresholdItDoesNotDoAnything() {
    Uri uri = Uri.parse("content://4");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_MUSIC);

    // Simulate proximity update
    proximitySensor.listener.onSensorChanged(mockSensorEvent(10f));


    verify(mediaPlayer, never()).stop();
    verify(mediaPlayer, never()).release();
    // earpiece is now set to true
    verify(mediaPlayerFactory, never()).create(eq(uri), anyObject(), eq(true));
  }

  @Test
  public void whenProximityUpdatesAgainItPausesAndRestartsWithUpdatedAudioOutputAgain() {
    Uri uri = Uri.parse("content://4");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_MUSIC);

    // Simulate proximity update
    proximitySensor.listener.onSensorChanged(mockSensorEvent(2f));

    // Simulate playback starts again
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    verify(mediaPlayer).stop();
    verify(mediaPlayer).release();
    // earpiece is now set to true
    verify(mediaPlayerFactory).create(eq(uri), anyObject(), eq(true));
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_VOICE_CALL);

    // Simulate proximity update again
    proximitySensor.listener.onSensorChanged(mockSensorEvent(200f));

    // Simulate playback starts yet again
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    verify(mediaPlayer).stop();
    verify(mediaPlayer).release();
    // earpiece is now set to true
    verify(mediaPlayerFactory).create(eq(uri), anyObject(), eq(false));
  }

  @Test
  public void whenProximityUpdatesAcquiresWakeLock() {
    Uri uri = Uri.parse("content://4");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_MUSIC);

    // Simulate proximity update
    proximitySensor.listener.onSensorChanged(mockSensorEvent(2f));

    isTrue(wakeLock.isAcquireCalled);
  }

  @Test
  public void whenProximityUpdatesAgainReleasesWakeLock() {
    Uri uri = Uri.parse("content://4");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_MUSIC);

    // Simulate proximity update
    proximitySensor.listener.onSensorChanged(mockSensorEvent(2f));

    // Simulate playback starts again
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    when(mediaPlayer.getAudioStreamType()).thenReturn(android.media.AudioManager.STREAM_VOICE_CALL);

    clock.progress(10000L);
    // Simulate proximity update again
    proximitySensor.listener.onSensorChanged(mockSensorEvent(200f));
    // Simulate playback starts yet again
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);

    isTrue(wakeLock.isReleaseCalled);
  }

  @Test
  public void wakeLockIsSetToReleaseWhenPlaybackReachesAtEnd() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate playback starts
    playerEventListener.onPlayerStateChanged(true, Player.STATE_READY);
    clock.progress(10000L);
    // Simulate acquired WakeLock
    wakeLock.acquire();
    // Simulate playback ends
    playerEventListener.onPlayerStateChanged(true, Player.STATE_ENDED);

    isTrue(wakeLock.isReleaseWaitForNoProximityCalled);
  }

  @Test
  public void wakeLockIsSetToReleaseWhenPlaybackFails() {
    Uri uri = Uri.parse("content://2");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    setupBound(uri, 0, mockListener);

    // Simulate acquired WakeLock
    wakeLock.acquire();

    clock.progress(10000L);
    // Simulate playback error
    ExoPlaybackException e = ExoPlaybackException.createForSource(new IOException());
    playerEventListener.onPlayerError(e);

    isTrue(wakeLock.isReleaseWaitForNoProximityCalled);
  }

  @Test
  public void closingNotificationStopService() {
    Uri uri = Uri.parse("content://5");
    AudioStateListener mockListener = mock(AudioStateListener.class);
    AudioPlayerServiceBackend.LocalBinder binder = setupBound(uri, 0, mockListener);

    doAnswer((InvocationOnMock invocation) -> {
      playerEventListener.onPlayerStateChanged(true, Player.STATE_IDLE);
      return null;
    }).when(mediaPlayer).stop();

    backend.onStartCommand(closeCommand());

    verify(mediaPlayer).release();
    verify(serviceInterface).stop();
  }

  /*
   * Helper methods
   */

  private Intent playCommand(Uri uri, double progress) {
    Intent intent = new Intent();
    intent.putExtra(AudioPlayerServiceBackend.MEDIA_URI_EXTRA, uri);
    intent.putExtra(AudioPlayerServiceBackend.PROGRESS_EXTRA, progress);
    intent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.PLAY);
    return intent;
  }

  private Intent pauseCommand() {
    Intent intent = new Intent();
    intent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.PAUSE);
    return intent;
  }

  private Intent resumeCommand() {
    Intent intent = new Intent();
    intent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.RESUME);
    return intent;
  }

  private Intent closeCommand() {
    Intent intent = new Intent();
    intent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.CLOSE);
    return intent;
  }

  /**
   * SensorEvent is not easily mocked and have dummy values because of some final fields.
   * This method is to hack the class by reflection to generate a desired SensorEvent for tests.
   */
  private SensorEvent mockSensorEvent(float value) {
    try {
      SensorEvent sensorEvent = mock(SensorEvent.class);

      Field sensorField = SensorEvent.class.getField("sensor");
      sensorField.setAccessible(true);
      Sensor sensor = mock(Sensor.class);
      when(sensor.getType()).thenReturn(Sensor.TYPE_PROXIMITY);
      sensorField.set(sensorEvent, sensor);

      Field valuesField = SensorEvent.class.getField("values");
      valuesField.setAccessible(true);
      valuesField.set(sensorEvent, new float[]{value});
      return sensorEvent;

    } catch (NoSuchFieldException | IllegalAccessException e) {
      fail(e);
      return null;
    }
  }

  /*
   * Fake classes of dependencies for tests.
   */

  private static class FakeProximitySensor implements ProximitySensor {
    SensorEventListener listener;
    @Override
    public float getMaximumRange() {
      return 50000f;
    }

    @Override
    public void registerListener(SensorEventListener listener, int samplingPeriodUs) {
      this.listener = listener;
    }

    @Override
    public void unregisterListener(SensorEventListener listener) {
      this.listener = null;
    }
  }

  private static class FakeWakeLock implements WakeLock {
    private boolean held;

    boolean isAcquireCalled;
    boolean isReleaseCalled;
    boolean isReleaseWaitForNoProximityCalled;

    @Override
    public void acquire() {
      held = true;
      isAcquireCalled = true;
    }

    @Override
    public void release() {
      held = false;
      isReleaseCalled = true;
    }

    @Override
    public void releaseWaitForNoProximity() {
      held = false;
      isReleaseWaitForNoProximityCalled = true;
    }

    @Override
    public boolean isHeld() {
      return held;
    }
  }

  private static class FakeClock implements Clock {
    private long currentTimeMillis;

    FakeClock(long currentTimeMillis) {
      this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    public long currentTimeMillis() {
      return currentTimeMillis;
    }

    public void progress(long millis) {
      currentTimeMillis += millis;
    }
  }
}

