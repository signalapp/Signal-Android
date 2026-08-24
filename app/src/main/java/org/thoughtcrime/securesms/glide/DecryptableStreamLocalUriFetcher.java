/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
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

  DecryptableStreamLocalUriFetcher(Context context, Uri uri, long thumbnailTimeUs) {
    super(context.getContentResolver(), uri);
    this.context          = context;
    this.thumbnailTimeUs  = thumbnailTimeUs;
  }

  @Override
  protected InputStream loadResource(Uri uri, ContentResolver contentResolver) throws FileNotFoundException {
    if (MediaUtil.hasVideoThumbnail(context, uri)) {
      long   timeUs    = thumbnailTimeUs > 0 ? thumbnailTimeUs : 1000;
      Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, uri, timeUs);

      if (thumbnail != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(baos.toByteArray());
        thumbnail.recycle();
        return thumbnailStream;
      }
      if (PartAuthority.isAttachmentUri(uri) && MediaUtil.isVideoType(PartAuthority.getAttachmentContentType(context, uri))) {
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
      } else if (isSafeSize(context, uri)) {
        return PartAuthority.getAttachmentThumbnailStream(context, uri);
      } else {
        throw new IOException("File dimensions are too large!");
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("PartAuthority couldn't load Uri resource.");
    }
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
