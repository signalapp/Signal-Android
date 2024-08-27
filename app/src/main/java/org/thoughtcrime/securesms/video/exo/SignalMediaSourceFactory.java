package org.thoughtcrime.securesms.video.exo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;

/**
 * This class is responsible for creating a MediaSource object for a given Uri, using {@link SignalDataSource.Factory}.
 */
@OptIn(markerClass = UnstableApi.class)
public final class SignalMediaSourceFactory implements MediaSource.Factory {

  private final ProgressiveMediaSource.Factory progressiveMediaSourceFactory;

  public SignalMediaSourceFactory(@NonNull Context context) {
    DataSource.Factory attachmentDataSourceFactory = new SignalDataSource.Factory(context, null, ExoPlayerPool.DataSourceTransferListener.INSTANCE);
    ExtractorsFactory  extractorsFactory           = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    progressiveMediaSourceFactory = new ProgressiveMediaSource.Factory(attachmentDataSourceFactory, extractorsFactory);
  }

  @Override
  public MediaSource.Factory setDrmSessionManagerProvider(@Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    return progressiveMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
  }

  @Override
  public MediaSource.Factory setLoadErrorHandlingPolicy(@Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    return progressiveMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
  }

  @Override
  public int[] getSupportedTypes() {
    return new int[] { C.CONTENT_TYPE_OTHER };
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    return progressiveMediaSourceFactory.createMediaSource(mediaItem);
  }
}
