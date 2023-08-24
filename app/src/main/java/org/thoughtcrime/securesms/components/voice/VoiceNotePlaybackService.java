package org.thoughtcrime.securesms.components.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.checkerframework.checker.units.qual.A;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.util.Collections;

/**
 * Android Service responsible for playback of voice notes.
 */
@OptIn(markerClass = UnstableApi.class)
public class VoiceNotePlaybackService extends MediaSessionService {

  public static final String ACTION_NEXT_PLAYBACK_SPEED = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.next_playback_speed";
  public static final String ACTION_SET_AUDIO_STREAM    = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.set_audio_stream";

  private static final String TAG                 = Log.tag(VoiceNotePlaybackService.class);
  private static final String SESSION_ID          = "VoiceNotePlayback";
  private static final String EMPTY_ROOT_ID       = "empty-root-id";
  private static final int    LOAD_MORE_THRESHOLD = 2;

  private MediaSession                         mediaSession;
  private VoiceNotePlayer                      player;
  private KeyClearedReceiver                   keyClearedReceiver;
  private VoiceNotePlayerCallback              voiceNotePlayerCallback;

  @Override
  public void onCreate() {
    super.onCreate();
    player = new VoiceNotePlayer(this);
    player.addListener(new VoiceNotePlayerEventListener());

    voiceNotePlayerCallback = new VoiceNotePlayerCallback(this, player);
    mediaSession            = new MediaSession.Builder(this, player).setCallback(voiceNotePlayerCallback).setId(SESSION_ID).build();
    keyClearedReceiver      = new KeyClearedReceiver(this, mediaSession.getToken());

    setMediaNotificationProvider(new VoiceNoteMediaNotificationProvider(this));
    setListener(new MediaSessionServiceListener());
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);

    mediaSession.getPlayer().stop();
    mediaSession.getPlayer().clearMediaItems();
  }

  @Override
  public void onDestroy() {
    player.release();
    mediaSession.release();
    mediaSession = null;
    clearListener();
    mediaSession = null;
    super.onDestroy();
    keyClearedReceiver.unregister();
  }

  @Nullable
  @Override
  public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
    return mediaSession;
  }

  private class VoiceNotePlayerEventListener implements Player.Listener {

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
      onPlaybackStateChanged(playWhenReady, player.getPlaybackState());
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
      onPlaybackStateChanged(player.getPlayWhenReady(), playbackState);
    }

    private void onPlaybackStateChanged(boolean playWhenReady, int playbackState) {
      switch (playbackState) {
        case Player.STATE_BUFFERING:
        case Player.STATE_READY:

          if (!playWhenReady) {
            stopForeground(false);
          } else {
            sendViewedReceiptForCurrentWindowIndex();
          }
          break;
        default:
      }
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
      int currentWindowIndex = newPosition.windowIndex;
      if (currentWindowIndex == C.INDEX_UNSET) {
        return;
      }

      if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
        sendViewedReceiptForCurrentWindowIndex();
        MediaItem currentMediaItem = player.getCurrentMediaItem();
        if (currentMediaItem != null && currentMediaItem.playbackProperties != null) {
          Log.d(TAG, "onPositionDiscontinuity: current window uri: " + currentMediaItem.playbackProperties.uri);
        }

        PlaybackParameters playbackParameters = getPlaybackParametersForWindowPosition(currentWindowIndex);

        final float speed = playbackParameters != null ? playbackParameters.speed : 1f;
        if (speed != player.getPlaybackParameters().speed) {
          player.setPlayWhenReady(false);
          if (playbackParameters != null) {
            player.setPlaybackParameters(playbackParameters);
          }
          player.seekTo(currentWindowIndex, 1);
          player.setPlayWhenReady(true);
        }
      }

      boolean isWithinThreshold = currentWindowIndex < LOAD_MORE_THRESHOLD ||
                                  currentWindowIndex + LOAD_MORE_THRESHOLD >= player.getMediaItemCount();

      if (isWithinThreshold && currentWindowIndex % 2 == 0) {
        voiceNotePlayerCallback.loadMoreVoiceNotes();
      }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
      Log.w(TAG, "ExoPlayer error occurred:", error);
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
      final int stream;
      if (audioAttributes.usage == C.USAGE_VOICE_COMMUNICATION) {
        stream = AudioManager.STREAM_VOICE_CALL;
      } else {
        stream = AudioManager.STREAM_MUSIC;
      }

      Log.i(TAG, "onAudioAttributesChanged: Setting audio stream to " + stream);
    }
  }

  private @Nullable PlaybackParameters getPlaybackParametersForWindowPosition(int currentWindowIndex) {
    if (isAudioMessage(currentWindowIndex)) {
    return player.getPlaybackParameters();
    } else {
      return null;
    }
  }

  private boolean isAudioMessage(int currentWindowIndex) {
    return currentWindowIndex % 2 == 0;
  }

  private void sendViewedReceiptForCurrentWindowIndex() {
    if (player.getPlaybackState() == Player.STATE_READY &&
        player.getPlayWhenReady() &&
        player.getCurrentWindowIndex() != C.INDEX_UNSET)
    {

      MediaItem currentMediaItem = player.getCurrentMediaItem();
      if (currentMediaItem == null || currentMediaItem.playbackProperties == null) {
        return;
      }

      Uri mediaUri = currentMediaItem.playbackProperties.uri;
      if (!mediaUri.getScheme().equals("content")) {
        return;
      }

      SignalExecutors.BOUNDED.execute(() -> {
        Bundle extras = currentMediaItem.mediaMetadata.extras;
        if (extras == null) {
          return;
        }
        long         messageId       = extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID);
        RecipientId  recipientId     = RecipientId.from(extras.getString(VoiceNoteMediaItemFactory.EXTRA_INDIVIDUAL_RECIPIENT_ID));
        MessageTable messageDatabase = SignalDatabase.messages();

        MessageTable.MarkedMessageInfo markedMessageInfo = messageDatabase.setIncomingMessageViewed(messageId);

        if (markedMessageInfo != null) {
          ApplicationDependencies.getJobManager().add(new SendViewedReceiptJob(markedMessageInfo.getThreadId(),
                                                                               recipientId,
                                                                               markedMessageInfo.getSyncMessageId().getTimetamp(),
                                                                               new MessageId(messageId)));
          MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(markedMessageInfo.getSyncMessageId()));
        }
      });
    }
  }

  /**
   * Receiver to stop playback and kill the notification if user locks signal via screen lock.
   * This registers itself as a receiver on the [Context] as soon as it can.
   */
  private static class KeyClearedReceiver extends BroadcastReceiver {
    private static final String TAG = Log.tag(KeyClearedReceiver.class);
    private static final IntentFilter KEY_CLEARED_FILTER = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);

    private final Context                           context;
    private final ListenableFuture<MediaController> controllerFuture;
    private       MediaController                   controller;

    private boolean registered;

    private KeyClearedReceiver(@NonNull Context context, @NonNull SessionToken token) {
      this.context     = context;
      Log.d(TAG, "Creating media controller…");
      controllerFuture = new MediaController.Builder(context, token).buildAsync();
      Futures.addCallback(controllerFuture, new FutureCallback<>() {
        @Override
        public void onSuccess(@Nullable MediaController result) {
          Log.d(TAG, "Successfully created media controller.");
          controller = result;
          register();
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
          Log.w(TAG, "KeyClearedReceiver.onFailure", t);
        }
      }, ContextCompat.getMainExecutor(context));
    }

    void register() {
      if (controller == null) {
        Log.w(TAG, "Failed to register KeyClearedReceiver because MediaController was null.");
        return;
      }
      
      if (!registered) {
        ContextCompat.registerReceiver(context, this, KEY_CLEARED_FILTER, ContextCompat.RECEIVER_NOT_EXPORTED);
        registered = true;
        Log.d(TAG, "Successfully registered.");
      }
    }

    void unregister() {
      if (registered) {
        context.unregisterReceiver(this);
        registered = false;
      }
      MediaController.releaseFuture(controllerFuture);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      if (controller == null) {
        Log.w(TAG, "Received broadcast but could not stop playback because MediaController was null.");
      } else {
        Log.i(TAG, "Received broadcast, stopping playback.");
        controller.stop();
      }
    }
  }

  private static class MediaSessionServiceListener implements Listener {
    @Override
    public void onForegroundServiceStartNotAllowedException() {
      Log.e(TAG, "Could not start VoiceNotePlaybackService, encountered a ForegroundServiceStartNotAllowedException.");
    }
  }
}
