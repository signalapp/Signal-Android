package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.video.exo.AttachmentMediaSourceFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * ExoPlayer Preparer for Voice Notes. This only supports ACTION_PLAY_FROM_URI
 */
final class VoiceNotePlaybackPreparer implements MediaSessionConnector.PlaybackPreparer {

  private static final String   TAG      = Log.tag(VoiceNotePlaybackPreparer.class);
  private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
  private static final long     LIMIT    = 5;

  public static final Uri NEXT_URI = Uri.parse("file:///android_asset/sounds/state-change_confirm-down.ogg");
  public static final Uri END_URI  = Uri.parse("file:///android_asset/sounds/state-change_confirm-up.ogg");

  private final Context                      context;
  private final SimpleExoPlayer              player;
  private final VoiceNoteQueueDataAdapter    queueDataAdapter;
  private final AttachmentMediaSourceFactory mediaSourceFactory;
  private final ConcatenatingMediaSource    dataSource;
  private final VoiceNotePlaybackParameters voiceNotePlaybackParameters;

  private boolean canLoadMore;
  private Uri     latestUri = Uri.EMPTY;

  VoiceNotePlaybackPreparer(@NonNull Context context,
                            @NonNull SimpleExoPlayer player,
                            @NonNull VoiceNoteQueueDataAdapter queueDataAdapter,
                            @NonNull AttachmentMediaSourceFactory mediaSourceFactory,
                            @NonNull VoiceNotePlaybackParameters voiceNotePlaybackParameters)
  {
    this.context            = context;
    this.player             = player;
    this.queueDataAdapter   = queueDataAdapter;
    this.mediaSourceFactory = mediaSourceFactory;
    this.dataSource                  = new ConcatenatingMediaSource();
    this.voiceNotePlaybackParameters = voiceNotePlaybackParameters;
  }

  @Override
  public long getSupportedPrepareActions() {
    return PlaybackStateCompat.ACTION_PLAY_FROM_URI;
  }

  @Override
  public void onPrepare() {
    throw new UnsupportedOperationException("VoiceNotePlaybackPreparer does not support onPrepare");
  }

  @Override
  public void onPrepareFromMediaId(String mediaId, Bundle extras) {
    throw new UnsupportedOperationException("VoiceNotePlaybackPreparer does not support onPrepareFromMediaId");
  }

  @Override
  public void onPrepareFromSearch(String query, Bundle extras) {
    throw new UnsupportedOperationException("VoiceNotePlaybackPreparer does not support onPrepareFromSearch");
  }

  @Override
  public void onPrepareFromUri(final Uri uri, Bundle extras) {
    Log.d(TAG, "onPrepareFromUri: " + uri);

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
                         return loadMediaDescriptionForSinglePlayback(messageId);
                       } else {
                         return loadMediaDescriptionForDraftPlayback(threadId, uri);
                       }
                     } else {
                       return loadMediaDescriptionsForConsecutivePlayback(messageId);
                     }
                   },
                   descriptions -> {
                     queueDataAdapter.clear();
                     dataSource.clear();

                     if (Util.hasItems(descriptions) && Objects.equals(latestUri, uri)) {
                       applyDescriptionsToQueue(descriptions);

                       int window = Math.max(0, queueDataAdapter.indexOf(uri));

                       player.addListener(new Player.EventListener() {
                         @Override
                         public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
                           if (timeline.getWindowCount() >= window) {
                             player.setPlayWhenReady(false);
                             player.setPlaybackParameters(voiceNotePlaybackParameters.getParameters());
                             player.seekTo(window, (long) (player.getDuration() * progress));
                             player.setPlayWhenReady(true);
                             player.removeListener(this);
                           }
                         }
                       });

                       player.prepare(dataSource);
                       canLoadMore = !singlePlayback;
                     } else if (Objects.equals(latestUri, uri)) {
                       Log.w(TAG, "Requested playback but no voice notes could be found.");
                       ThreadUtil.postToMain(() -> Toast.makeText(context, R.string.VoiceNotePlaybackPreparer__failed_to_play_voice_message, Toast.LENGTH_SHORT)
                                                        .show());
                     }
                   });
  }

  @Override
  public String[] getCommands() {
    return new String[0];
  }

  @Override
  public void onCommand(Player player, String command, Bundle extras, ResultReceiver cb) {
  }

  @MainThread
  private void applyDescriptionsToQueue(@NonNull List<MediaDescriptionCompat> descriptions) {
    for (MediaDescriptionCompat description : descriptions) {
      int                    holderIndex  = queueDataAdapter.indexOf(description.getMediaUri());
      MediaDescriptionCompat next         = createNextClone(description);
      int                    currentIndex = player.getCurrentWindowIndex();

      if (holderIndex != -1) {
        queueDataAdapter.remove(holderIndex);

        if (!queueDataAdapter.isEmpty()) {
          queueDataAdapter.remove(holderIndex);
        }

        queueDataAdapter.add(holderIndex, createNextClone(description));
        queueDataAdapter.add(holderIndex, description);

        if (currentIndex != holderIndex) {
          dataSource.removeMediaSource(holderIndex);
          dataSource.addMediaSource(holderIndex, mediaSourceFactory.createMediaSource(description));
        }

        if (currentIndex != holderIndex + 1) {
          if (dataSource.getSize() > 1) {
            dataSource.removeMediaSource(holderIndex + 1);
          }

          dataSource.addMediaSource(holderIndex + 1, mediaSourceFactory.createMediaSource(next));
        }
      } else {
        int insertLocation = queueDataAdapter.indexAfter(description);

        queueDataAdapter.add(insertLocation, next);
        queueDataAdapter.add(insertLocation, description);

        dataSource.addMediaSource(insertLocation, mediaSourceFactory.createMediaSource(next));
        dataSource.addMediaSource(insertLocation, mediaSourceFactory.createMediaSource(description));
      }
    }

    int                    lastIndex = queueDataAdapter.size() - 1;
    MediaDescriptionCompat last      = queueDataAdapter.getMediaDescription(lastIndex);

    if (Objects.equals(last.getMediaUri(), NEXT_URI)) {
      queueDataAdapter.remove(lastIndex);
      dataSource.removeMediaSource(lastIndex);

      if (queueDataAdapter.size() > 1) {
        MediaDescriptionCompat end = createEndClone(last);

        queueDataAdapter.add(lastIndex, end);
        dataSource.addMediaSource(lastIndex, mediaSourceFactory.createMediaSource(end));
      }
    }

    if (queueDataAdapter.size() != dataSource.getSize()) {
      throw new IllegalStateException("QueueDataAdapter and DataSource size inconsistency.");
    }
  }

  private @NonNull MediaDescriptionCompat createEndClone(@NonNull MediaDescriptionCompat source) {
    return buildUpon(source).setMediaId("end").setMediaUri(END_URI).build();
  }

  private @NonNull MediaDescriptionCompat createNextClone(@NonNull MediaDescriptionCompat source) {
    return buildUpon(source).setMediaId("next").setMediaUri(NEXT_URI).build();
  }

  private @NonNull MediaDescriptionCompat.Builder buildUpon(@NonNull MediaDescriptionCompat source) {
    return new MediaDescriptionCompat.Builder()
                                     .setSubtitle(source.getSubtitle())
                                     .setDescription(source.getDescription())
                                     .setTitle(source.getTitle())
                                     .setIconUri(source.getIconUri())
                                     .setIconBitmap(source.getIconBitmap())
                                     .setMediaId(source.getMediaId())
                                     .setExtras(source.getExtras());
  }

  public void loadMoreVoiceNotes() {
    if (!canLoadMore) {
      return;
    }

    MediaDescriptionCompat mediaDescriptionCompat = queueDataAdapter.getMediaDescription(player.getCurrentWindowIndex());
    if (Objects.equals(mediaDescriptionCompat, VoiceNoteQueueDataAdapter.EMPTY)) {
      return;
    }

    long messageId = mediaDescriptionCompat.getExtras().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_MESSAGE_ID);

    SimpleTask.run(EXECUTOR,
                   () -> loadMediaDescriptionsForConsecutivePlayback(messageId),
                   descriptions -> {
                     if (Util.hasItems(descriptions) && canLoadMore) {
                       applyDescriptionsToQueue(descriptions);
                     }
                   });
  }

  private @NonNull List<MediaDescriptionCompat> loadMediaDescriptionForSinglePlayback(long messageId) {
    try {
      MessageRecord messageRecord = DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId);

      if (!MessageRecordUtil.hasAudio(messageRecord)) {
        Log.w(TAG, "Message does not contain audio.");
        return Collections.emptyList();
      }

      MediaDescriptionCompat mediaDescriptionCompat = VoiceNoteMediaDescriptionCompatFactory.buildMediaDescription(context ,messageRecord);
      if (mediaDescriptionCompat == null) {
        return Collections.emptyList();
      } else {
        return Collections.singletonList(mediaDescriptionCompat);
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Could not find message.", e);
      return Collections.emptyList();
    }
  }

  private @NonNull List<MediaDescriptionCompat> loadMediaDescriptionForDraftPlayback(long threadId, @NonNull Uri draftUri) {
    return Collections.singletonList(VoiceNoteMediaDescriptionCompatFactory.buildMediaDescription(context, threadId, draftUri));
  }

  @WorkerThread
  private @NonNull List<MediaDescriptionCompat> loadMediaDescriptionsForConsecutivePlayback(long messageId) {
    try {
      List<MessageRecord> recordsAfter  = DatabaseFactory.getMmsSmsDatabase(context).getMessagesAfterVoiceNoteInclusive(messageId, LIMIT);

      return buildFilteredMessageRecordList(recordsAfter).stream()
                                                         .map(record -> VoiceNoteMediaDescriptionCompatFactory.buildMediaDescription(context, record))
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
}
