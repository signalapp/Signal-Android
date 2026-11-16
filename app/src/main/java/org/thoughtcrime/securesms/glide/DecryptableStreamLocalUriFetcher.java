/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;
import kotlin.Pair;

import com.bumptech.glide.load.data.StreamLocalUriFetcher;

import org.signal.core.util.logging.Log;
import org.signal.glide.common.io.GlideStreamConfig;
import org.signal.core.models.database.AttachmentId;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.signal.core.util.contentproviders.BlobProvider;
import org.signal.core.util.bitmaps.BitmapDecodingException;
import org.signal.core.util.bitmaps.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

class DecryptableStreamLocalUriFetcher extends StreamLocalUriFetcher {

  private static final String TAG = Log.tag(DecryptableStreamLocalUriFetcher.class);

  private static final long TOTAL_PIXEL_SIZE_LIMIT = 210_000_000L; // 210 megapixels

  private final Context context;
  private final long    thumbnailTimeUs;
  private final String  contentType;

  private Boolean hasVideoThumbnail;
  private boolean videoFrameLoaded;
  private byte[]  videoFrame;
  private Boolean safeSize;

  /**
   * Instances memoize the expensive checks and the extracted video frame, so a single instance should be reused for every stream opened for the same
   * uri during a load.
   *
   * @param contentType The content type of the data at the uri, if known. Avoids a database lookup to determine whether it's a video.
   */
  DecryptableStreamLocalUriFetcher(Context context, Uri uri, long thumbnailTimeUs, @Nullable String contentType) {
    super(context.getContentResolver(), uri);
    this.context          = context;
    this.thumbnailTimeUs  = thumbnailTimeUs;
    this.contentType      = contentType;
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
    if (hasVideoThumbnail(uri)) {
      byte[] frame = getVideoFrame(uri);
      if (frame != null) {
        return new ByteArrayInputStream(frame);
      }

      if (PartAuthority.isAttachmentUri(uri) && MediaUtil.isVideoType(contentType != null ? contentType : PartAuthority.getAttachmentContentType(context, uri))) {
        try {
          AttachmentId attachmentId = PartAuthority.requireAttachmentId(uri);
          Uri          thumbnailUri = PartAuthority.getAttachmentThumbnailUri(attachmentId);
          InputStream  thumbStream  = PartAuthority.getAttachmentThumbnailStream(context, thumbnailUri);
          if (thumbStream != null) {
            return thumbStream;
          }
        } catch (IOException e) {
          Log.i(TAG, "Failed to fetch thumbnail", e);
        }
      }
    }

    try {
      if (PartAuthority.isBlobUri(uri) && BlobProvider.isSingleUseMemoryBlob(uri)) {
        return PartAuthority.getAttachmentThumbnailStream(context, uri);
      } else if (isSafeSizeMemoized(uri)) {
        return PartAuthority.getAttachmentThumbnailStream(context, uri);
      } else {
        throw new IOException("File dimensions are too large!");
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("PartAuthority couldn't load Uri resource.");
    }
  }

  private synchronized boolean hasVideoThumbnail(Uri uri) {
    if (hasVideoThumbnail == null) {
      hasVideoThumbnail = contentType != null ? MediaUtil.isVideoType(contentType) : MediaUtil.hasVideoThumbnail(context, uri);
    }
    return hasVideoThumbnail;
  }

  private synchronized @Nullable byte[] getVideoFrame(Uri uri) {
    if (videoFrameLoaded) {
      return videoFrame;
    }

    long   timeUs    = thumbnailTimeUs > 0 ? thumbnailTimeUs : 1000;
    Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, uri, timeUs);

    if (thumbnail != null) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, baos);
      thumbnail.recycle();
      videoFrame = baos.toByteArray();
    }

    videoFrameLoaded = true;
    return videoFrame;
  }

  private synchronized boolean isSafeSizeMemoized(Uri uri) throws IOException {
    if (safeSize == null) {
      safeSize = isSafeSize(context, uri);
    }
    return safeSize;
  }

  private boolean isSafeSize(Context context, Uri uri) throws IOException {
    try {
      InputStream            stream      = PartAuthority.getAttachmentThumbnailStream(context, uri);
      Pair<Integer, Integer> dimensions  = BitmapUtil.getDimensions(stream);
      long                   totalPixels = (long) dimensions.getFirst() * dimensions.getSecond();
      boolean                safe        = totalPixels < TOTAL_PIXEL_SIZE_LIMIT;

      if (!safe) {
        Log.w(TAG, "Unsafe size! (" + dimensions.getFirst() + " x " + dimensions.getSecond() + ") = " + totalPixels);
      }

      return safe;
    } catch (BitmapDecodingException e) {
      Long size = PartAuthority.getAttachmentSize(context, uri);
      return size != null && size < GlideStreamConfig.getMarkReadLimitBytes();
    }
  }
}
