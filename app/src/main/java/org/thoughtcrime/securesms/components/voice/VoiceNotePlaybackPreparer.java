package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ExoPlayer Preparer for Voice Notes. This only supports ACTION_PLAY_FROM_URI
 */
final class VoiceNotePlaybackPreparer implements MediaSessionConnector.PlaybackPreparer {

  private static final String   TAG      = Log.tag(VoiceNotePlaybackPreparer.class);
  private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

  private final Context                                context;
  private final SimpleExoPlayer                        player;
  private final VoiceNoteQueueDataAdapter              queueDataAdapter;
  private final TimelineQueueEditor.MediaSourceFactory mediaSourceFactory;

  VoiceNotePlaybackPreparer(@NonNull Context context,
                            @NonNull SimpleExoPlayer player,
                            @NonNull VoiceNoteQueueDataAdapter queueDataAdapter,
                            @NonNull TimelineQueueEditor.MediaSourceFactory mediaSourceFactory)
  {
    this.context            = context;
    this.player             = player;
    this.queueDataAdapter   = queueDataAdapter;
    this.mediaSourceFactory = mediaSourceFactory;
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
  public void onPrepareFromUri(Uri uri, Bundle extras) {
    long messageId = extras.getLong(VoiceNoteMediaController.EXTRA_MESSAGE_ID);
    long position  = extras.getLong(VoiceNoteMediaController.EXTRA_PLAYHEAD, 0);

    SimpleTask.run(EXECUTOR,
                   () -> VoiceNoteMediaDescriptionCompatFactory.buildMediaDescription(context, uri, messageId),
                   description -> {
                     if (description == null) {
                       Toast.makeText(context, R.string.VoiceNotePlaybackPreparer__could_not_start_playback, Toast.LENGTH_SHORT)
                            .show();
                       Log.w(TAG, "onPrepareFromUri: could not start playback");
                       return;
                     }

                     queueDataAdapter.add(description);
                     player.seekTo(position);
                     player.prepare(Objects.requireNonNull(mediaSourceFactory.createMediaSource(description)),
                                    position == 0,
                                    false);
                   });
  }

  @Override
  public String[] getCommands() {
    return new String[0];
  }

  @Override
  public void onCommand(Player player, String command, Bundle extras, ResultReceiver cb) {
  }
}
