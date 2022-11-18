package org.thoughtcrime.securesms.giph.mp4;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import org.thoughtcrime.securesms.video.exo.ChunkedDataSourceFactory;

import okhttp3.OkHttpClient;

/**
 * Factory which creates MediaSource objects for given Giphy URIs
 */
final class GiphyMp4MediaSourceFactory {

  private final DataSource.Factory           dataSourceFactory;
  private final ExtractorsFactory            extractorsFactory;
  private final ExtractorMediaSource.Factory extractorMediaSourceFactory;

  GiphyMp4MediaSourceFactory(@NonNull OkHttpClient okHttpClient) {
    dataSourceFactory           = new ChunkedDataSourceFactory(okHttpClient, null);
    extractorsFactory           = new DefaultExtractorsFactory();
    extractorMediaSourceFactory = new ExtractorMediaSource.Factory(dataSourceFactory).setExtractorsFactory(extractorsFactory);
  }

  @NonNull MediaSource create(@NonNull Uri uri) {
    return extractorMediaSourceFactory.createMediaSource(uri);
  }
}
