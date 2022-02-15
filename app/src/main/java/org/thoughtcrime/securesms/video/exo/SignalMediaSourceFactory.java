package org.thoughtcrime.securesms.video.exo;

import android.content.Context;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import java.util.List;

/**
 * This class is responsible for creating a MediaSource object for a given Uri, using {@link SignalDataSource.Factory}.
 */
@SuppressWarnings("deprecation")
public final class SignalMediaSourceFactory implements MediaSourceFactory {

  private final ProgressiveMediaSource.Factory progressiveMediaSourceFactory;

  public SignalMediaSourceFactory(@NonNull Context context) {
    DataSource.Factory attachmentDataSourceFactory = new SignalDataSource.Factory(context, null, null);
    ExtractorsFactory  extractorsFactory           = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    progressiveMediaSourceFactory = new ProgressiveMediaSource.Factory(attachmentDataSourceFactory, extractorsFactory);
  }

  /**
   * Creates a MediaSource for a given MediaDescriptionCompat
   *
   * @param description The description to build from
   *
   * @return A preparable MediaSource
   */
  public @NonNull MediaSource createMediaSource(MediaDescriptionCompat description) {
    return progressiveMediaSourceFactory.createMediaSource(
        new MediaItem.Builder().setUri(description.getMediaUri()).setTag(description).build()
    );
  }

  @Override
  public MediaSourceFactory setStreamKeys(@Nullable List<StreamKey> streamKeys) {
    return progressiveMediaSourceFactory.setStreamKeys(streamKeys);
  }

  @Override
  public MediaSourceFactory setDrmSessionManagerProvider(@Nullable DrmSessionManagerProvider drmSessionManagerProvider) {
    return progressiveMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
  }

  @Override
  public MediaSourceFactory setDrmSessionManager(@Nullable DrmSessionManager drmSessionManager) {
    return progressiveMediaSourceFactory.setDrmSessionManager(drmSessionManager);
  }

  @Override
  public MediaSourceFactory setDrmHttpDataSourceFactory(@Nullable HttpDataSource.Factory drmHttpDataSourceFactory) {
    return progressiveMediaSourceFactory.setDrmHttpDataSourceFactory(drmHttpDataSourceFactory);
  }

  @Override
  public MediaSourceFactory setDrmUserAgent(@Nullable String userAgent) {
    return progressiveMediaSourceFactory.setDrmUserAgent(userAgent);
  }

  @Override
  public MediaSourceFactory setLoadErrorHandlingPolicy(@Nullable LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
    return progressiveMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
  }

  @Override
  public int[] getSupportedTypes() {
    return new int[] { C.TYPE_OTHER };
  }

  @Override
  public MediaSource createMediaSource(MediaItem mediaItem) {
    return progressiveMediaSourceFactory.createMediaSource(mediaItem);
  }

  @Override
  public MediaSource createMediaSource(Uri uri) {
    return progressiveMediaSourceFactory.createMediaSource(uri);
  }
}
