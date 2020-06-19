package org.thoughtcrime.securesms.audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.service.AudioPlayerService;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend.AudioStateListener;
import org.thoughtcrime.securesms.service.AudioPlayerServiceBackend.LocalBinder;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class AudioSlidePlayer implements AudioStateListener {

  private static final String TAG = AudioSlidePlayer.class.getSimpleName();

  private static @NonNull Optional<AudioSlidePlayer> playing = Optional.absent();

  private final @NonNull  Context           context;
  private final @NonNull  AudioSlide        slide;
  private final @NonNull  Intent            serviceIntent;
  private final @NonNull  ServiceConnection serviceConnection;

  private @NonNull  WeakReference<Listener> listener;
  private @Nullable LocalBinder             binder;

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
    this.serviceIntent        = new Intent(context, AudioPlayerService.class);
    this.serviceConnection    = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        binder = (LocalBinder) iBinder;
        binder.setListener(AudioSlidePlayer.this);
      }

      @Override
      public void onServiceDisconnected(ComponentName componentName) {
        // Service was killed. Notify the view.
        binder = null;
        removePlaying(AudioSlidePlayer.this);
        notifyOnStop();
      }
    };
  }

  private void startService(final Uri uri, final double progress) {
    serviceIntent.putExtra(AudioPlayerServiceBackend.MEDIA_URI_EXTRA, uri);
    serviceIntent.putExtra(AudioPlayerServiceBackend.PROGRESS_EXTRA, progress);
    serviceIntent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.PLAY);
    context.startService(serviceIntent);
    bindService();
  }

  private void bindService() {
    context.bindService(serviceIntent, serviceConnection, 0);
  }

  private void unbindService() {
    if (binder == null) return;
    context.unbindService(serviceConnection);
    binder = null;
  }

  public void play(final double progress) throws IOException {
    Uri uri = slide.getUri();
    if (slide.getUri() == null) {
      throw new IOException("Slide has no URI!");
    }

    setPlaying(this);
    startService(uri, progress);
  }

  public synchronized void stop() {
    Log.i(TAG, "Stop called!");

    removePlaying(this);
    if (binder != null) {
      binder.stop();
    }
  }

  public synchronized void stopService() {
    serviceIntent.putExtra(AudioPlayerServiceBackend.COMMAND_EXTRA, AudioPlayerServiceBackend.Command.CLOSE);
    // The name of the method is unfortunate but the intention here is to stop the service that may be running
    context.startService(serviceIntent);
  }

  public static void onResume() {
    if (!playing.isPresent()) return;
    AudioSlidePlayer player = playing.get();
    player.bindService();
  }

  public static void onPause() {
    if (!playing.isPresent()) return;
    AudioSlidePlayer player = playing.get();
    player.unbindService();
  }

  public static void onDestroy() {
    if (!playing.isPresent()) return;
    AudioSlidePlayer player = playing.get();
    player.stopService();
  }

  @Override
  public void onAudioStarted() {
    notifyOnStart();
  }

  @Override
  public void onAudioStopped() {
    unbindService();
    notifyOnStop();
  }

  @Override
  public void onAudioError(ExoPlaybackException error) {
    Toast.makeText(context, R.string.AudioSlidePlayer_error_playing_audio, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAudioProgress(double progress, long millis) {
    notifyOnProgress(progress, millis);
  }

  public void setListener(@NonNull Listener listener) {
    this.listener = new WeakReference<>(listener);
  }

  public @NonNull AudioSlide getAudioSlide() {
    return slide;
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

  public interface Listener {
    void onStart();
    void onStop();
    void onProgress(double progress, long millis);
  }
}
