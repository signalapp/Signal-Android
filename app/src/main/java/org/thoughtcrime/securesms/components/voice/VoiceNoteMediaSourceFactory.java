package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.thoughtcrime.securesms.video.exo.AttachmentDataSourceFactory;

/**
 * This class is responsible for creating a MediaSource object for a given MediaDescriptionCompat
 */
final class VoiceNoteMediaSourceFactory {

  private final Context context;

  VoiceNoteMediaSourceFactory(Context context) {
    this.context = context;
  }

  /**
   * Creates a MediaSource for a given MediaDescriptionCompat
   *
   * @param description The description to build from
   *
   * @return A preparable MediaSource
   */
  public @Nullable MediaSource createMediaSource(MediaDescriptionCompat description) {
    DefaultDataSourceFactory    defaultDataSourceFactory    = new DefaultDataSourceFactory(context, "GenericUserAgent", null);
    AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(context, defaultDataSourceFactory, null);
    ExtractorsFactory           extractorsFactory           = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    return new ExtractorMediaSource.Factory(attachmentDataSourceFactory)
                                   .setExtractorsFactory(extractorsFactory)
                                   .createMediaSource(description.getMediaUri());
  }
}
