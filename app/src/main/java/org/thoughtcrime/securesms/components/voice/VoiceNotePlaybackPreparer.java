package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.Util;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * ExoPlayer Preparer for Voice Notes. This only supports ACTION_PLAY_FROM_URI
 */
final class   VoiceNotePlaybackPreparer implements MediaSessionConnector.PlaybackPreparer {

  private static final String   TAG      = Log.tag(VoiceNotePlaybackPreparer.class);
  private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
  private static final long     LIMIT    = 5;

  private final Context                     context;
  private final Player                      player;
  private final VoiceNotePlaybackParameters voiceNotePlaybackParameters;

  private boolean canLoadMore;
  private Uri     latestUri = Uri.EMPTY;

  VoiceNotePlaybackPreparer(@NonNull Context context,
                            @NonNull Player player,
                            @NonNull VoiceNotePlaybackParameters voiceNotePlaybackParameters)
  {
    this.context                     = context;
    this.player                      = player;
    this.voiceNotePlaybackParameters = voiceNotePlaybackParameters;
  }

  @Override
  public long getSupportedPrepareActions() {
    return PlaybackStateCompat.ACTION_PLAY_FROM_URI;
  }

  @Override
  public void onPrepare(boolean playWhenReady) {
    Log.w(TAG, "Requested playback from IDLE state. Ignoring.");
  }

  @Override
  public void onPrepareFromMediaId(@NonNull String mediaId, boolean playWhenReady, @Nullable Bundle extras) {
    throw new UnsupportedOperationException("VoiceNotePlaybackPreparer does not support onPrepareFromMediaId");
  }

  @Override
  public void onPrepareFromSearch(@NonNull String query, boolean playWhenReady, @Nullable Bundle extras) {
    throw new UnsupportedOperationException("VoiceNotePlaybackPreparer does not support onPrepareFromSearch");
  }

  @Override
  public void onPrepareFromUri(@NonNull Uri uri, boolean playWhenReady, @Nullable Bundle extras) {
    Log.d(TAG, "onPrepareFromUri: " + uri);
    if (extras == null) {
      return;
    }

    long    messageId      = extras.getLong(VoiceNoteMediaController.EXTRA_MESSAGE_ID);
    long    threadId       = extras.getLong(VoiceNoteMediaController.EXTRA_THREAD_ID);
    double  progress       = extras.getDouble(VoiceNoteMediaController.EXTRA_PROGRESS, 0);
    boolean singlePlayback = extras.getBoolean(VoiceNoteMediaController.EXTRA_PLAY_SINGLE, false);

    canLoadMore = false;
    latestUri   = uri;

    SimpleTask.run(EXECUTOR,
                   () -> {
                     if (singlePlayback) {
                       if (messageId != -1) {
                         return loadMediaItemsForSinglePlayback(messageId);
                       } else {
                         return loadMediaItemsForDraftPlayback(threadId, uri);
                       }
                     } else {
                       return loadMediaItemsForConsecutivePlayback(messageId);
                     }
                   },
                   mediaItems -> {
                     player.clearMediaItems();

                     if (Util.hasItems(mediaItems) && Objects.equals(latestUri, uri)) {
                       applyDescriptionsToQueue(mediaItems);

                       int window = Math.max(0, indexOfPlayerMediaItemByUri(uri));

                       player.addListener(new Player.Listener() {
                         @Override
                         public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
                           if (timeline.getWindowCount() >= window) {
                             player.setPlayWhenReady(false);
                             player.setPlaybackParameters(voiceNotePlaybackParameters.getParameters());
                             player.seekTo(window, (long) (player.getDuration() * progress));
                             player.setPlayWhenReady(true);
                             player.removeListener(this);
                           }
                         }
                       });

                       player.prepare();
                       canLoadMore = !singlePlayback;
                     } else if (Objects.equals(latestUri, uri)) {
                       Log.w(TAG, "Requested playback but no voice notes could be found.");
                       ThreadUtil.postToMain(() -> {
                         Toast.makeText(context, R.string.VoiceNotePlaybackPreparer__failed_to_play_voice_message, Toast.LENGTH_SHORT)
                              .show();
                       });
                     }
                   });
  }

  @MainThread
  private void applyDescriptionsToQueue(@NonNull List<MediaItem> mediaItems) {
    for (MediaItem mediaItem : mediaItems) {
      MediaItem.PlaybackProperties playbackProperties = mediaItem.playbackProperties;
      if (playbackProperties == null) {
        continue;
      }
      int       holderIndex  = indexOfPlayerMediaItemByUri(playbackProperties.uri);
      MediaItem next         = VoiceNoteMediaItemFactory.buildNextVoiceNoteMediaItem(mediaItem);
      int       currentIndex = player.getCurrentWindowIndex();

      if (holderIndex != -1) {
        if (currentIndex != holderIndex) {
          player.removeMediaItem(holderIndex);
          player.addMediaItem(holderIndex, mediaItem);
        }

        if (currentIndex != holderIndex + 1) {
          if (player.getMediaItemCount() > 1) {
            player.removeMediaItem(holderIndex + 1);
          }

          player.addMediaItem(holderIndex + 1, next);
        }
      } else {
        int insertLocation = indexAfter(mediaItem);

        player.addMediaItem(insertLocation, next);
        player.addMediaItem(insertLocation, mediaItem);
      }
    }

    int itemsCount = player.getMediaItemCount();
    if (itemsCount > 0) {
      int       lastIndex = itemsCount - 1;
      MediaItem last      = player.getMediaItemAt(lastIndex);

      if (last.playbackProperties != null &&
          Objects.equals(last.playbackProperties.uri, VoiceNoteMediaItemFactory.NEXT_URI))
      {
        player.removeMediaItem(lastIndex);

        if (player.getMediaItemCount() > 1) {
          MediaItem end = VoiceNoteMediaItemFactory.buildEndVoiceNoteMediaItem(last);

          player.addMediaItem(lastIndex, end);
        }
      }
    }
  }

  private int indexOfPlayerMediaItemByUri(@NonNull Uri uri) {
    for (int i = 0; i < player.getMediaItemCount(); i++) {
      MediaItem.PlaybackProperties playbackProperties = player.getMediaItemAt(i).playbackProperties;
      if (playbackProperties != null && playbackProperties.uri.equals(uri)) {
        return i;
      }
    }
    return -1;
  }

  private int indexAfter(@NonNull MediaItem target) {
    int  size            = player.getMediaItemCount();
    long targetMessageId = target.mediaMetadata.extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID);
    for (int i = 0; i < size; i++) {
      MediaMetadata mediaMetadata = player.getMediaItemAt(i).mediaMetadata;
      long          messageId     = mediaMetadata.extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID);

      if (messageId > targetMessageId) {
        return i;
      }
    }
    return size;
  }

  public void loadMoreVoiceNotes() {
    if (!canLoadMore) {
      return;
    }

    MediaItem currentMediaItem = player.getCurrentMediaItem();
    if (currentMediaItem == null) {
      return;
    }

    long messageId = currentMediaItem.mediaMetadata.extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID);

    SimpleTask.run(EXECUTOR,
                   () -> loadMediaItemsForConsecutivePlayback(messageId),
                   mediaItems -> {
                     if (Util.hasItems(mediaItems) && canLoadMore) {
                       applyDescriptionsToQueue(mediaItems);
                     }
                   });
  }

  private @NonNull List<MediaItem> loadMediaItemsForSinglePlayback(long messageId) {
    try {
      MessageRecord messageRecord = SignalDatabase.messages()
                                                  .getMessageRecord(messageId);

      if (!MessageRecordUtil.hasAudio(messageRecord)) {
        Log.w(TAG, "Message does not contain audio.");
        return Collections.emptyList();
      }

      MediaItem mediaItem = VoiceNoteMediaItemFactory.buildMediaItem(context, messageRecord);
      if (mediaItem == null) {
        return Collections.emptyList();
      } else {
        return Collections.singletonList(mediaItem);
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Could not find message.", e);
      return Collections.emptyList();
    }
  }

  private @NonNull List<MediaItem> loadMediaItemsForDraftPlayback(long threadId, @NonNull Uri draftUri) {
    return Collections
        .singletonList(VoiceNoteMediaItemFactory.buildMediaItem(context, threadId, draftUri));
  }

  @WorkerThread
  private @NonNull List<MediaItem> loadMediaItemsForConsecutivePlayback(long messageId) {
    try {
      List<MessageRecord> recordsAfter = SignalDatabase.messages().getMessagesAfterVoiceNoteInclusive(messageId, LIMIT);

      return buildFilteredMessageRecordList(recordsAfter).stream()
                                                         .map(record -> VoiceNoteMediaItemFactory
                                                             .buildMediaItem(context, record))
                                                         .filter(Objects::nonNull)
                                                         .collect(Collectors.toList());
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Could not find message.", e);
      return Collections.emptyList();
    }
  }

  private static @NonNull List<MessageRecord> buildFilteredMessageRecordList(@NonNull List<MessageRecord> recordsAfter) {
    return Stream.of(recordsAfter)
                 .takeWhile(MessageRecordUtil::hasAudio)
                 .toList();
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean onCommand(@NonNull Player player,
                           @NonNull String command,
                           @Nullable Bundle extras,
                           @Nullable ResultReceiver cb)
  {
    return false;
  }
}
