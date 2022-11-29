package org.thoughtcrime.securesms.components.voice;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;
import org.thoughtcrime.securesms.jobs.UnableToStartException;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.util.Collections;
import java.util.List;

/**
 * Android Service responsible for playback of voice notes.
 */
public class VoiceNotePlaybackService extends MediaBrowserServiceCompat {

  public static final String ACTION_NEXT_PLAYBACK_SPEED = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.next_playback_speed";
  public static final String ACTION_SET_AUDIO_STREAM    = "org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackService.action.set_audio_stream";

  private static final String TAG                 = Log.tag(VoiceNotePlaybackService.class);
  private static final String EMPTY_ROOT_ID       = "empty-root-id";
  private static final int    LOAD_MORE_THRESHOLD = 2;

  private static final long SUPPORTED_ACTIONS = PlaybackStateCompat.ACTION_PLAY |
                                                PlaybackStateCompat.ACTION_PAUSE |
                                                PlaybackStateCompat.ACTION_SEEK_TO |
                                                PlaybackStateCompat.ACTION_STOP |
                                                PlaybackStateCompat.ACTION_PLAY_PAUSE;

  private MediaSessionCompat           mediaSession;
  private MediaSessionConnector        mediaSessionConnector;
  private VoiceNotePlayer              player;
  private BecomingNoisyReceiver        becomingNoisyReceiver;
  private KeyClearedReceiver           keyClearedReceiver;
  private VoiceNoteNotificationManager voiceNoteNotificationManager;
  private VoiceNotePlaybackPreparer    voiceNotePlaybackPreparer;
  private boolean                      isForegroundService;
  private VoiceNotePlaybackParameters  voiceNotePlaybackParameters;

  @Override
  public void onCreate() {
    super.onCreate();

    mediaSession                 = new MediaSessionCompat(this, TAG);
    voiceNotePlaybackParameters  = new VoiceNotePlaybackParameters(mediaSession);
    mediaSessionConnector        = new MediaSessionConnector(mediaSession);
    becomingNoisyReceiver        = new BecomingNoisyReceiver(this, mediaSession.getSessionToken());
    keyClearedReceiver           = new KeyClearedReceiver(this, mediaSession.getSessionToken());
    player                       = new VoiceNotePlayer(this);
    voiceNoteNotificationManager = new VoiceNoteNotificationManager(this,
                                                                    mediaSession.getSessionToken(),
                                                                    new VoiceNoteNotificationManagerListener());
    voiceNotePlaybackPreparer    = new VoiceNotePlaybackPreparer(this, player, voiceNotePlaybackParameters);

    player.addListener(new VoiceNotePlayerEventListener());

    mediaSessionConnector.setPlayer(player);
    mediaSessionConnector.setEnabledPlaybackActions(SUPPORTED_ACTIONS);
    mediaSessionConnector.setPlaybackPreparer(voiceNotePlaybackPreparer);
    mediaSessionConnector.setQueueNavigator(new VoiceNoteQueueNavigator(mediaSession));

    VoiceNotePlaybackController voiceNotePlaybackController = new VoiceNotePlaybackController(player.getInternalPlayer(), voiceNotePlaybackParameters);
    mediaSessionConnector.registerCustomCommandReceiver(voiceNotePlaybackController);

    setSessionToken(mediaSession.getSessionToken());

    mediaSession.setActive(true);
    keyClearedReceiver.register();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);

    player.stop();
    player.clearMediaItems();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mediaSession.setActive(false);
    mediaSession.release();
    becomingNoisyReceiver.unregister();
    keyClearedReceiver.unregister();
    player.release();
  }

  @Override
  public @Nullable BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
    if (clientUid == Process.myUid()) {
      return new BrowserRoot(EMPTY_ROOT_ID, null);
    } else {
      return null;
    }
  }

  @Override
  public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
    result.sendResult(Collections.emptyList());
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
          voiceNoteNotificationManager.showNotification(player);

          if (!playWhenReady) {
            stopForeground(false);
            isForegroundService = false;
            becomingNoisyReceiver.unregister();
          } else {
            sendViewedReceiptForCurrentWindowIndex();
            becomingNoisyReceiver.register();
          }
          break;
        default:
          becomingNoisyReceiver.unregister();
          voiceNoteNotificationManager.hideNotification();
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
        voiceNotePlaybackPreparer.loadMoreVoiceNotes();
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
      mediaSession.setPlaybackToLocal(stream);
    }
  }

  private @Nullable PlaybackParameters getPlaybackParametersForWindowPosition(int currentWindowIndex) {
    if (isAudioMessage(currentWindowIndex)) {
      return voiceNotePlaybackParameters.getParameters();
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
        long            messageId       = extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID);
        RecipientId  recipientId     = RecipientId.from(extras.getString(VoiceNoteMediaItemFactory.EXTRA_INDIVIDUAL_RECIPIENT_ID));
        MessageTable messageDatabase = SignalDatabase.mms();

        MessageTable.MarkedMessageInfo markedMessageInfo = messageDatabase.setIncomingMessageViewed(messageId);

        if (markedMessageInfo != null) {
          ApplicationDependencies.getJobManager().add(new SendViewedReceiptJob(markedMessageInfo.getThreadId(),
                                                                               recipientId,
                                                                               markedMessageInfo.getSyncMessageId().getTimetamp(),
                                                                               new MessageId(messageId, true)));
          MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(markedMessageInfo.getSyncMessageId()));
        }
      });
    }
  }

  private class VoiceNoteNotificationManagerListener implements PlayerNotificationManager.NotificationListener {

    @Override
    public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
      if (ongoing && !isForegroundService) {
        try {
          ForegroundServiceUtil.startWhenCapable(getApplicationContext(), new Intent(getApplicationContext(), VoiceNotePlaybackService.class));
          startForeground(notificationId, notification);
          isForegroundService = true;
        } catch (UnableToStartException e) {
          Log.e(TAG, "Unable to start foreground service!", e);
        }
      }
    }

    @Override
    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
      stopForeground(true);
      isForegroundService = false;
      stopSelf();
    }
  }

  /**
   * Receiver to stop playback and kill the notification if user locks signal via screen lock.
   */
  private static class KeyClearedReceiver extends BroadcastReceiver {
    private static final IntentFilter KEY_CLEARED_FILTER = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);

    private final Context               context;
    private final MediaControllerCompat controller;

    private boolean registered;

    private KeyClearedReceiver(@NonNull Context context, @NonNull MediaSessionCompat.Token token) {
      this.context    = context;
      this.controller = new MediaControllerCompat(context, token);
    }

    void register() {
      if (!registered) {
        context.registerReceiver(this, KEY_CLEARED_FILTER);
        registered = true;
      }
    }

    void unregister() {
      if (registered) {
        context.unregisterReceiver(this);
        registered = false;
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      controller.getTransportControls().stop();
    }
  }

  /**
   * Receiver to pause playback when things become noisy.
   */
  private static class BecomingNoisyReceiver extends BroadcastReceiver {
    private static final IntentFilter NOISY_INTENT_FILTER = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private final Context               context;
    private final MediaControllerCompat controller;

    private boolean registered;

    private BecomingNoisyReceiver(Context context, MediaSessionCompat.Token token) {
      this.context    = context;
      this.controller = new MediaControllerCompat(context, token);
    }

    void register() {
      if (!registered) {
        context.registerReceiver(this, NOISY_INTENT_FILTER);
        registered = true;
      }
    }

    void unregister() {
      if (registered) {
        context.unregisterReceiver(this);
        registered = false;
      }
    }

    public void onReceive(Context context, @NonNull Intent intent) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
        controller.getTransportControls().pause();
      }
    }
  }
}
