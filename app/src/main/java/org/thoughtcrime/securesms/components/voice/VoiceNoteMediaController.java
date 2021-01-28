package org.thoughtcrime.securesms.components.voice;

import android.content.ComponentName;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.signal.core.util.logging.Log;

import java.util.Objects;

/**
 * Encapsulates control of voice note playback from an Activity component.
 *
 * This class assumes that it will be created within the scope of Activity#onCreate
 *
 * The workhorse of this repository is the ProgressEventHandler, which will supply a
 * steady stream of update events to the set callback.
 */
public class VoiceNoteMediaController implements DefaultLifecycleObserver {

  public static final String EXTRA_MESSAGE_ID = "voice.note.message_id";
  public static final String EXTRA_PROGRESS = "voice.note.playhead";
  public static final String EXTRA_PLAY_SINGLE = "voice.note.play.single";

  private static final String TAG = Log.tag(VoiceNoteMediaController.class);

  private MediaBrowserCompat                      mediaBrowser;
  private AppCompatActivity                       activity;
  private ProgressEventHandler                    progressEventHandler;
  private MutableLiveData<VoiceNotePlaybackState> voiceNotePlaybackState = new MutableLiveData<>(VoiceNotePlaybackState.NONE);

  private final MediaControllerCompatCallback mediaControllerCompatCallback = new MediaControllerCompatCallback();

  public VoiceNoteMediaController(@NonNull AppCompatActivity activity) {
    this.activity     = activity;
    this.mediaBrowser = new MediaBrowserCompat(activity,
                                               new ComponentName(activity, VoiceNotePlaybackService.class),
                                               new ConnectionCallback(),
                                               null);

    activity.getLifecycle().addObserver(this);
  }

  public LiveData<VoiceNotePlaybackState> getVoiceNotePlaybackState() {
    return voiceNotePlaybackState;
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    mediaBrowser.connect();
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner) {
    activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    clearProgressEventHandler();

    if (MediaControllerCompat.getMediaController(activity) != null) {
      MediaControllerCompat.getMediaController(activity).unregisterCallback(mediaControllerCompatCallback);
    }
    mediaBrowser.disconnect();
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner) {
    activity.getLifecycle().removeObserver(this);
    activity = null;
  }

  private static boolean isPlayerActive(@NonNull PlaybackStateCompat playbackStateCompat) {
    return playbackStateCompat.getState() == PlaybackStateCompat.STATE_BUFFERING ||
           playbackStateCompat.getState() == PlaybackStateCompat.STATE_PLAYING;
  }

  private @NonNull MediaControllerCompat getMediaController() {
    return MediaControllerCompat.getMediaController(activity);
  }


  public void startConsecutivePlayback(@NonNull Uri audioSlideUri, long messageId, double progress) {
    startPlayback(audioSlideUri, messageId, progress, false);
  }

  public void startSinglePlayback(@NonNull Uri audioSlideUri, long messageId, double progress) {
    startPlayback(audioSlideUri, messageId, progress, true);
  }

  /**
   * Tells the Media service to begin playback of a given audio slide. If the audio
   * slide is currently playing, we jump to the desired position and then begin playback.
   *
   * @param audioSlideUri  The Uri of the desired audio slide
   * @param messageId      The Message id of the given audio slide
   * @param progress       The desired progress % to seek to.
   * @param singlePlayback The player will only play back the specified Uri, and not build a playlist.
   */
  private void startPlayback(@NonNull Uri audioSlideUri, long messageId, double progress, boolean singlePlayback) {
    if (isCurrentTrack(audioSlideUri)) {
      long duration = getMediaController().getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

      getMediaController().getTransportControls().seekTo((long) (duration * progress));
      getMediaController().getTransportControls().play();
    } else {
      Bundle extras = new Bundle();
      extras.putLong(EXTRA_MESSAGE_ID, messageId);
      extras.putDouble(EXTRA_PROGRESS, progress);
      extras.putBoolean(EXTRA_PLAY_SINGLE, singlePlayback);

      getMediaController().getTransportControls().playFromUri(audioSlideUri, extras);
    }
  }

  /**
   * Pauses playback if the given audio slide is playing.
   *
   * @param audioSlideUri The Uri of the audio slide to pause.
   */
  public void pausePlayback(@NonNull Uri audioSlideUri) {
    if (isCurrentTrack(audioSlideUri)) {
      getMediaController().getTransportControls().pause();
    }
  }

  /**
   * Seeks to a given position if th given audio slide is playing. This call
   * is ignored if the given audio slide is not currently playing.
   *
   * @param audioSlideUri The Uri of the audio slide to seek.
   * @param progress      The progress percentage to seek to.
   */
  public void seekToPosition(@NonNull Uri audioSlideUri, double progress) {
    if (isCurrentTrack(audioSlideUri)) {
      long duration = getMediaController().getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

      getMediaController().getTransportControls().pause();
      getMediaController().getTransportControls().seekTo((long) (duration * progress));
      getMediaController().getTransportControls().play();
    }
  }

  /**
   * Stops playback if the given audio slide is playing
   *
   * @param audioSlideUri The Uri of the audio slide to stop
   */
  public void stopPlaybackAndReset(@NonNull Uri audioSlideUri) {
    if (isCurrentTrack(audioSlideUri)) {
      getMediaController().getTransportControls().stop();
    }
  }

  private boolean isCurrentTrack(@NonNull Uri uri) {
    MediaMetadataCompat metadataCompat = getMediaController().getMetadata();

    return metadataCompat != null && Objects.equals(metadataCompat.getDescription().getMediaUri(), uri);
  }

  private void notifyProgressEventHandler() {
    if (progressEventHandler == null) {
      progressEventHandler = new ProgressEventHandler(getMediaController(), voiceNotePlaybackState);
      progressEventHandler.sendEmptyMessage(0);
    }
  }

  private void clearProgressEventHandler() {
    if (progressEventHandler != null) {
      progressEventHandler = null;
    }
  }

  private final class ConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
    @Override
    public void onConnected() {
      try {
        MediaSessionCompat.Token token           = mediaBrowser.getSessionToken();
        MediaControllerCompat    mediaController = new MediaControllerCompat(activity, token);

        MediaControllerCompat.setMediaController(activity, mediaController);

        mediaController.registerCallback(mediaControllerCompatCallback);

        mediaControllerCompatCallback.onPlaybackStateChanged(mediaController.getPlaybackState());
      } catch (RemoteException e) {
        Log.w(TAG, "onConnected: Failed to set media controller", e);
      }
    }
  }

  private static class ProgressEventHandler extends Handler {

    private final MediaControllerCompat                   mediaController;
    private final MutableLiveData<VoiceNotePlaybackState> voiceNotePlaybackState;

    private ProgressEventHandler(@NonNull MediaControllerCompat mediaController,
                                 @NonNull MutableLiveData<VoiceNotePlaybackState> voiceNotePlaybackState)
    {
      super(Looper.getMainLooper());

      this.mediaController        = mediaController;
      this.voiceNotePlaybackState = voiceNotePlaybackState;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      MediaMetadataCompat mediaMetadataCompat = mediaController.getMetadata();
      if (isPlayerActive(mediaController.getPlaybackState()) &&
          mediaMetadataCompat != null                        &&
          mediaMetadataCompat.getDescription() != null       &&
          mediaMetadataCompat.getDescription().getMediaUri() != null)
      {

        Uri                    mediaUri      = Objects.requireNonNull(mediaMetadataCompat.getDescription().getMediaUri());
        boolean                autoReset     = Objects.equals(mediaUri, VoiceNotePlaybackPreparer.NEXT_URI) || Objects.equals(mediaUri, VoiceNotePlaybackPreparer.END_URI);
        VoiceNotePlaybackState previousState = voiceNotePlaybackState.getValue();
        long                   position      = mediaController.getPlaybackState().getPosition();
        long                   duration      = mediaMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);

        if (previousState != null && Objects.equals(mediaUri, previousState.getUri())) {
          if (position < 0 && previousState.getPlayheadPositionMillis() >= 0) {
            position = previousState.getPlayheadPositionMillis();
          }

          if (duration <= 0 && previousState.getTrackDuration() > 0) {
            duration = previousState.getTrackDuration();
          }
        }

        if (duration > 0 && position >= 0 && position <= duration) {
          voiceNotePlaybackState.postValue(new VoiceNotePlaybackState(mediaUri, position, duration, autoReset));
        }

        sendEmptyMessageDelayed(0, 50);
      } else {
        voiceNotePlaybackState.postValue(VoiceNotePlaybackState.NONE);
      }
    }
  }

  private final class MediaControllerCompatCallback extends MediaControllerCompat.Callback {
    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
      if (isPlayerActive(state)) {
        notifyProgressEventHandler();
      } else {
        clearProgressEventHandler();
      }
    }
  }
}
