/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ContentTypeUtil;
import org.signal.core.util.logging.Log;
import org.signal.core.util.bitmaps.BitmapDecodingException;
import org.signal.core.util.bitmaps.BitmapUtil;
import org.thoughtcrime.securesms.video.TranscodingConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import kotlin.Pair;

public abstract class MediaConstraints {
  private static final String TAG = Log.tag(MediaConstraints.class);

  public abstract int getImageMaxWidth();
  public abstract int getImageMaxHeight();
  public abstract int getImageMaxSize();

  public abstract List<TranscodingConfig.QualityTier> getVideoTranscodingSettings();

  /**
   * Provide a list of dimensions that should be attempted during compression. We will keep moving
   * down the list until the image can be scaled to fit under {@link #getImageMaxSize()}.
   * The first entry in the list should match your max width/height.
   */
  public abstract int[] getImageDimensionTargets();

  public abstract long getGifMaxSize();
  public abstract long getVideoMaxSize();
  public abstract long getAudioMaxSize();
  public abstract long getDocumentMaxSize();
  public abstract long getMaxAttachmentSize();

  public @IntRange(from = 0, to = 100) int getImageCompressionQualitySetting() {
    return 70;
  }

  public long getUncompressedVideoMaxSize() {
    return getVideoMaxSize();
  }

  public long getCompressedVideoMaxSize() {
    return getVideoMaxSize();
  }

  public long getEditorVideoMaxSize() {
    return isVideoTranscodeAvailable() ? getCompressedVideoMaxSize() : getVideoMaxSize();
  }

  public boolean isSatisfied(@NonNull Context context, @NonNull Uri uri, @NonNull String contentType, long size) {
    try {
      if (size > getMaxAttachmentSize()) {
        return false;
      }
      return (ContentTypeUtil.isGif(contentType)       && size <= getGifMaxSize() && isWithinBounds(context, uri))   ||
             (ContentTypeUtil.isImageType(contentType) && size <= getImageMaxSize() && isWithinBounds(context, uri)) ||
             (ContentTypeUtil.isAudioType(contentType) && size <= getAudioMaxSize())                                 ||
             (ContentTypeUtil.isVideoType(contentType) && size <= getVideoMaxSize())                                 ||
             ((ContentTypeUtil.isDocumentType(contentType) || ContentTypeUtil.isLongTextType(contentType)) && size <= getDocumentMaxSize());
    } catch (IOException ioe) {
      Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
      return false;
    }
  }

  private boolean isWithinBounds(Context context, Uri uri) throws IOException {
    try {
      InputStream            is         = MediaSendDependencies.INSTANCE.getMediaSendRepository().getAttachmentStream(context, uri);
      Pair<Integer, Integer> dimensions = BitmapUtil.getDimensions(is);
      return dimensions.getFirst() > 0 && dimensions.getFirst() <= getImageMaxWidth() &&
             dimensions.getSecond() > 0 && dimensions.getSecond() <= getImageMaxHeight();
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public boolean canResize(@Nullable String mediaType) {
    return ContentTypeUtil.isImageType(mediaType) && !ContentTypeUtil.isGif(mediaType) ||
           ContentTypeUtil.isVideoType(mediaType) && isVideoTranscodeAvailable();
  }

  @ChecksSdkIntAtLeast(api = 26)
  public static boolean isVideoTranscodeAvailable() {
    return Build.VERSION.SDK_INT >= 26;
  }
}
