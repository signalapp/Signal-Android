package org.thoughtcrime.securesms.video.exo;

import android.content.Context;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

/**
 * This class is responsible for creating a MediaSource object for a given Uri, using AttachmentDataSourceFactory
 */
public final class AttachmentMediaSourceFactory {

  private final ExtractorMediaSource.Factory extractorMediaSourceFactory;

  public AttachmentMediaSourceFactory(@NonNull Context context) {
    DefaultDataSourceFactory    defaultDataSourceFactory    = new DefaultDataSourceFactory(context, "GenericUserAgent", null);
    AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(context, defaultDataSourceFactory, null);
    ExtractorsFactory           extractorsFactory           = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    extractorMediaSourceFactory = new ExtractorMediaSource.Factory(attachmentDataSourceFactory)
                                                          .setExtractorsFactory(extractorsFactory);
  }

  /**
   * Creates a MediaSource for a given MediaDescriptionCompat
   *
   * @param description The description to build from
   *
   * @return A preparable MediaSource
   */
  public @NonNull MediaSource createMediaSource(MediaDescriptionCompat description) {
    return createMediaSource(description.getMediaUri());
  }

  /**
   * Creates a MediaSource for a given Uri
   */
  public @NonNull MediaSource createMediaSource(Uri uri) {
    return extractorMediaSourceFactory.createMediaSource(uri);
  }
}
