package org.thoughtcrime.securesms.giph.mp4;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import org.thoughtcrime.securesms.video.exo.ChunkedDataSourceFactory;

import okhttp3.OkHttpClient;

/**
 * Factory which creates MediaSource objects for given Giphy URIs
 */
final class GiphyMp4MediaSourceFactory {

  private final ProgressiveMediaSource.Factory progressiveMediaSourceFactory;

  GiphyMp4MediaSourceFactory(@NonNull OkHttpClient okHttpClient) {
    DataSource.Factory dataSourceFactory = new ChunkedDataSourceFactory(okHttpClient, null);
    progressiveMediaSourceFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
  }

  @NonNull MediaSource create(@NonNull Uri uri) {
    return progressiveMediaSourceFactory.createMediaSource(MediaItem.fromUri(uri));
  }
}
