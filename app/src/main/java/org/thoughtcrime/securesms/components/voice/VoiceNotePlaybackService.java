package org.thoughtcrime.securesms.components.voice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
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

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.util.Collections;
import java.util.List;

/**
 * Android Service responsible for playback of voice notes.
 */
@OptIn(markerClass = UnstableApi.class)
public class VoiceNotePlaybackService extends MediaSessionService {

  public static final String ACTION_NEXT_PLAYBACK_SPEED = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.next_playback_speed";
  public static final String ACTION_SET_AUDIO_STREAM    = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.set_audio_stream";

  private static final String TAG                 = Log.tag(VoiceNotePlaybackService.class);
  private static final String SESSION_ID          = "VoiceNotePlayback";
  private static final int    LOAD_MORE_THRESHOLD = 2;

  private MediaSession                         mediaSession;
  private VoiceNotePlayer                      player;
  private KeyClearedReceiver                   keyClearedReceiver;
  private VoiceNotePlayerCallback              voiceNotePlayerCallback;

  private final DatabaseObserver.Observer attachmentDeletionObserver = this::onAttachmentDeleted;

  @Override
  public void onCreate() {
    super.onCreate();
    player = new VoiceNotePlayer(this);
    player.addListener(new VoiceNotePlayerEventListener());

    voiceNotePlayerCallback = new VoiceNotePlayerCallback(this, player);

    final MediaSession session = buildMediaSession(false);
    if (session == null) {
      Log.e(TAG, "Unable to create media session at all, stopping service to avoid crash.");
      stopSelf();
      return;
    } else {
      mediaSession = session;
    }

    keyClearedReceiver = new KeyClearedReceiver(this, session.getToken());

    setMediaNotificationProvider(new VoiceNoteMediaNotificationProvider(this));
    setListener(new MediaSessionServiceListener());
    AppDependencies.getDatabaseObserver().registerAttachmentDeletedObserver(attachmentDeletionObserver);
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);

    final MediaSession session = mediaSession;
    if (session != null) {
      session.getPlayer().stop();
      session.getPlayer().clearMediaItems();
    }
  }

  @Override
  public void onDestroy() {
    AppDependencies.getDatabaseObserver().unregisterObserver(attachmentDeletionObserver);

    final VoiceNotePlayer voiceNotePlayer = player;
    if (voiceNotePlayer != null) {
      voiceNotePlayer.release();
    }

    MediaSession session = mediaSession;
    if (session != null) {
      session.release();
      mediaSession = null;
    }

    KeyClearedReceiver receiver = keyClearedReceiver;
    if (receiver != null) {
      receiver.unregister();
    }

    clearListener();
    super.onDestroy();
  }

  @Nullable
  @Override
  public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
    return mediaSession;
  }

  private class VoiceNotePlayerEventListener implements Player.Listener {
    private int previousPlaybackState = player.getPlaybackState();

    @Override
    public void onPlaybackStateChanged(int playbackState) {
      boolean playWhenReady = player.getPlayWhenReady();
      Log.d(TAG, "[onPlaybackStateChanged] playbackState: " + playbackStateToString(playbackState) + "\tplayWhenReady: " + playWhenReady);
      switch (playbackState) {
        case Player.STATE_BUFFERING, Player.STATE_READY -> {
          if (!playWhenReady) {
            stopForeground(false);
          } else {
            sendViewedReceiptForCurrentWindowIndex();
          }
        }
        case Player.STATE_ENDED -> {
          if (previousPlaybackState == Player.STATE_READY) {
            player.clearMediaItems();
          }
        }
        default -> {
        }
      }
      previousPlaybackState = playbackState;
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
      int mediaItemIndex = newPosition.mediaItemIndex;
      if (mediaItemIndex == C.INDEX_UNSET) {
        return;
      }

      if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
        sendViewedReceiptForCurrentWindowIndex();
        MediaItem currentMediaItem = player.getCurrentMediaItem();
        if (currentMediaItem != null && currentMediaItem.playbackProperties != null) {
          Log.d(TAG, "onPositionDiscontinuity: current window uri: " + currentMediaItem.playbackProperties.uri);
        }

        PlaybackParameters playbackParameters = getPlaybackParametersForWindowPosition(mediaItemIndex);

        final float speed = playbackParameters != null ? playbackParameters.speed : 1f;
        if (speed != player.getPlaybackParameters().speed) {
          player.setPlayWhenReady(false);
          if (playbackParameters != null) {
            player.setPlaybackParameters(playbackParameters);
          }
          player.seekTo(mediaItemIndex, 1);
          player.setPlayWhenReady(true);
        }
      } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
        player.setPlayWhenReady(true);
      }

      boolean isWithinThreshold = mediaItemIndex < LOAD_MORE_THRESHOLD ||
                                  mediaItemIndex + LOAD_MORE_THRESHOLD >= player.getMediaItemCount();

      if (isWithinThreshold && mediaItemIndex % 2 == 0) {
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

  private void onAttachmentDeleted() {
    Log.d(TAG, "Database attachment observer invoked.");
    ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
      if (player != null) {
        final MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null || currentItem.playbackProperties == null) {
          Log.d(TAG, "Current item is null or playback properties are null.");
          return;
        }

        final Uri currentlyPlayingUri = currentItem.playbackProperties.uri;

        if (currentlyPlayingUri == VoiceNoteMediaItemFactory.NEXT_URI || currentlyPlayingUri == VoiceNoteMediaItemFactory.END_URI) {
          Log.v(TAG, "Attachment deleted while voice note service was playing a system tone.");
        }

        try {
          final AttachmentId       partId     = new PartUriParser(currentlyPlayingUri).getPartId();
          final DatabaseAttachment attachment = SignalDatabase.attachments().getAttachment(partId);
          if (attachment == null) {
            player.stop();
            int playingIndex = player.getCurrentMediaItemIndex();
            player.removeMediaItem(playingIndex);
            Log.d(TAG, "Currently playing item removed.");
          } else {
            Log.d(TAG, "Attachment was not null, therefore not deleted, therefore no action taken.");
          }
        } catch (NumberFormatException ex) {
          Log.w(TAG, "Could not parse currently playing URI into an attachmentId.", ex);
        }
      }
    });
  }

  /**
   * Some devices, such as the ASUS Zenfone 8, erroneously report multiple broadcast receivers for {@value Intent#ACTION_MEDIA_BUTTON} in the package manager.
   * This triggers a failure within the {@link MediaSession} initialization and throws an {@link IllegalStateException}.
   * This method will catch that exception and attempt to disable the duplicated broadcast receiver in the hopes of getting the package manager to
   * report only 1, avoiding the error.
   * If that doesn't work, it returns null, signaling the {@link MediaSession} cannot be built on this device.
   * The opposite problem also appears to happen: the device reports that it cannot assign the media button receiver, which is required by AndroidX Media3.
   * This is despite the fact that Media3 confirms the presence of the receiver before attempting to bind.
   * In this case, the system throws an {@link IllegalArgumentException}, which we catch. Then we also disable the existing receiver, which should be the same
   * as if we had never had the received in the first place, which should cause Media3 to abort trying to bind to it and allow it to proceed.
   *
   * @return the built MediaSession, or null if the session cannot be built.
   */
  private @Nullable MediaSession buildMediaSession(boolean isRetry) {
    try {
      return new MediaSession.Builder(this, player).setCallback(voiceNotePlayerCallback).setId(SESSION_ID).build();
    } catch (IllegalStateException | IllegalArgumentException e) {

      if (isRetry) {
        Log.e(TAG, "Unable to create media session, even after retry.", e);
        return null;
      }

      Log.w(TAG, "Unable to create media session with default parameters.", e);
      PackageManager pm          = this.getPackageManager();
      Intent         queryIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
      queryIntent.setPackage(this.getPackageName());
      final List<ResolveInfo> mediaButtonReceivers = pm.queryBroadcastReceivers(queryIntent, /* flags= */ 0);

      Log.d(TAG, "Found " + mediaButtonReceivers.size() + " BroadcastReceivers for " + Intent.ACTION_MEDIA_BUTTON);

      boolean found = false;

      if (mediaButtonReceivers.size() > 1) {
        for (ResolveInfo receiverInfo : mediaButtonReceivers) {

          final ActivityInfo activityInfo = receiverInfo.activityInfo;

          if (!found && activityInfo.packageName.contains("androidx.media.session")) {
            found = true;
          } else {
            pm.setComponentEnabledSetting(new ComponentName(activityInfo.packageName, activityInfo.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
          }
        }

        return buildMediaSession(true);
      } else {
        return null;
      }
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
          AppDependencies.getJobManager().add(new SendViewedReceiptJob(markedMessageInfo.getThreadId(),
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

  private String playbackStateToString(int playbackState) {
    return switch (playbackState) {
      case Player.STATE_IDLE -> "Player.STATE_IDLE";
      case Player.STATE_BUFFERING -> "Player.STATE_BUFFERING";
      case Player.STATE_READY -> "Player.STATE_READY";
      case Player.STATE_ENDED -> "Player.STATE_ENDED";
      default -> "UNKNOWN(" + playbackState + ")";
    };
  }
}
