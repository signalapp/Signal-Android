package org.thoughtcrime.securesms.components.voice;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.video.exo.AttachmentMediaSourceFactory;

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
  private PlaybackStateCompat.Builder  stateBuilder;
  private SimpleExoPlayer              player;
  private BecomingNoisyReceiver        becomingNoisyReceiver;
  private KeyClearedReceiver           keyClearedReceiver;
  private VoiceNoteNotificationManager voiceNoteNotificationManager;
  private VoiceNoteQueueDataAdapter    queueDataAdapter;
  private VoiceNotePlaybackPreparer    voiceNotePlaybackPreparer;
  private boolean                      isForegroundService;
  private VoiceNotePlaybackParameters  voiceNotePlaybackParameters;

  private final LoadControl loadControl = new DefaultLoadControl.Builder()
                                                                .setBufferDurationsMs(Integer.MAX_VALUE,
                                                                                      Integer.MAX_VALUE,
                                                                                      Integer.MAX_VALUE,
                                                                                      Integer.MAX_VALUE)
                                                                .createDefaultLoadControl();

  @Override
  public void onCreate() {
    super.onCreate();

    mediaSession                 = new MediaSessionCompat(this, TAG);
    voiceNotePlaybackParameters  = new VoiceNotePlaybackParameters(mediaSession);
    stateBuilder                 = new PlaybackStateCompat.Builder()
                                                          .setActions(SUPPORTED_ACTIONS)
                                                          .addCustomAction(ACTION_NEXT_PLAYBACK_SPEED, "speed", R.drawable.ic_toggle_24);
    mediaSessionConnector        = new MediaSessionConnector(mediaSession, new VoiceNotePlaybackController(voiceNotePlaybackParameters));
    becomingNoisyReceiver        = new BecomingNoisyReceiver(this, mediaSession.getSessionToken());
    keyClearedReceiver           = new KeyClearedReceiver(this, mediaSession.getSessionToken());
    player                       = ExoPlayerFactory.newSimpleInstance(this, new DefaultRenderersFactory(this), new DefaultTrackSelector(), loadControl);
    queueDataAdapter             = new VoiceNoteQueueDataAdapter();
    voiceNoteNotificationManager = new VoiceNoteNotificationManager(this,
                                                                    mediaSession.getSessionToken(),
                                                                    new VoiceNoteNotificationManagerListener(),
                                                                    queueDataAdapter);

    AttachmentMediaSourceFactory mediaSourceFactory = new AttachmentMediaSourceFactory(this);

    voiceNotePlaybackPreparer = new VoiceNotePlaybackPreparer(this, player, queueDataAdapter, mediaSourceFactory, voiceNotePlaybackParameters);

    mediaSession.setPlaybackState(stateBuilder.build());

    player.addListener(new VoiceNotePlayerEventListener());
    player.setAudioAttributes(new AudioAttributes.Builder()
                                                 .setContentType(C.CONTENT_TYPE_SPEECH)
                                                 .setUsage(C.USAGE_MEDIA)
                                                 .build(), true);

    mediaSessionConnector.setPlayer(player, voiceNotePlaybackPreparer);
    mediaSessionConnector.setQueueNavigator(new VoiceNoteQueueNavigator(mediaSession, queueDataAdapter));

    setSessionToken(mediaSession.getSessionToken());

    mediaSession.setActive(true);
    keyClearedReceiver.register();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    super.onTaskRemoved(rootIntent);

    player.stop(true);
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

  private class VoiceNotePlayerEventListener implements Player.EventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      switch (playbackState) {
        case Player.STATE_BUFFERING:
        case Player.STATE_READY:
          voiceNoteNotificationManager.showNotification(player);

          if (!playWhenReady) {
            stopForeground(false);
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
    public void onPositionDiscontinuity(int reason) {
      int currentWindowIndex = player.getCurrentWindowIndex();
      if (currentWindowIndex == C.INDEX_UNSET) {
        return;
      }

      if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
        sendViewedReceiptForCurrentWindowIndex();
        MediaDescriptionCompat mediaDescriptionCompat = queueDataAdapter.getMediaDescription(currentWindowIndex);
        Log.d(TAG, "onPositionDiscontinuity: current window uri: " + mediaDescriptionCompat.getMediaUri());

        PlaybackParameters playbackParameters = getPlaybackParametersForWindowPosition(currentWindowIndex);

        final float speed = playbackParameters != null ? playbackParameters.speed : 1f;
        if (speed != player.getPlaybackParameters().speed) {
          player.setPlayWhenReady(false);
          player.setPlaybackParameters(playbackParameters);
          player.seekTo(currentWindowIndex, 1);
          player.setPlayWhenReady(true);
        }
      }

      boolean isWithinThreshold = currentWindowIndex < LOAD_MORE_THRESHOLD ||
                                  currentWindowIndex + LOAD_MORE_THRESHOLD >= queueDataAdapter.size();

      if (isWithinThreshold && currentWindowIndex % 2 == 0) {
        voiceNotePlaybackPreparer.loadMoreVoiceNotes();
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      Log.w(TAG, "ExoPlayer error occurred:", error);
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

      final MediaDescriptionCompat descriptionCompat = queueDataAdapter.getMediaDescription(player.getCurrentWindowIndex());

      if (!descriptionCompat.getMediaUri().getScheme().equals("content")) {
        return;
      }

      SignalExecutors.BOUNDED.execute(() -> {
        Bundle          extras          = descriptionCompat.getExtras();
        long            messageId       = extras.getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_MESSAGE_ID);
        RecipientId     recipientId     = RecipientId.from(extras.getString(VoiceNoteMediaDescriptionCompatFactory.EXTRA_INDIVIDUAL_RECIPIENT_ID));
        MessageDatabase messageDatabase = DatabaseFactory.getMmsDatabase(this);

        MessageDatabase.MarkedMessageInfo markedMessageInfo = messageDatabase.setIncomingMessageViewed(messageId);

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
    public void onNotificationStarted(int notificationId, Notification notification) {
      if (!isForegroundService) {
        ContextCompat.startForegroundService(getApplicationContext(), new Intent(getApplicationContext(), VoiceNotePlaybackService.class));
        startForeground(notificationId, notification);
        isForegroundService = true;
      }
    }

    @Override
    public void onNotificationCancelled(int notificationId) {
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
      this.context = context;
      try {
        this.controller = new MediaControllerCompat(context, token);
      } catch (RemoteException e) {
        throw new IllegalArgumentException("Failed to create controller from token", e);
      }
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
      this.context = context;
      try {
        this.controller = new MediaControllerCompat(context, token);
      } catch (RemoteException e) {
        throw new IllegalArgumentException("Failed to create controller from token", e);
      }
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
