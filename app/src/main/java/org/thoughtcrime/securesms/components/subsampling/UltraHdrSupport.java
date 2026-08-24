/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.subsampling;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;

/**
 * Helpers for detecting UltraHDR (gain map bearing) images in the full-screen media preview.
 */
public final class UltraHdrSupport {

  private UltraHdrSupport() {}

  /**
   * Cheap pre-filter for whether to install gain-map-aware decoders; {@link #hasGainmap(Bitmap)} is the
   * authoritative answer. API 35 is the feature floor for the whole HDR preview: without
   * {@code Window#setDesiredHdrHeadroom} the compositor dims the SDR chrome sharing the preview window.
   */
  public static boolean isEligible(@NonNull Uri uri, @Nullable String contentType) {
    return Build.VERSION.SDK_INT >= 35 &&
           isGainmapCapableContentType(contentType) &&
           PartAuthority.isLocalUri(uri);
  }

  /**
   * Whether the provided bitmap carries an UltraHDR gain map. The API 34 guard is
   * {@link Bitmap#hasGainmap()}'s own floor, not the preview's; {@link #isEligible(Uri, String)} applies that.
   */
  public static boolean hasGainmap(@Nullable Bitmap bitmap) {
    return Build.VERSION.SDK_INT >= 34 && bitmap != null && bitmap.hasGainmap();
  }

  /**
   * Containers that can carry a gain map. Deliberately wider than what every platform version can actually
   * parse: a bitmap without a gain map simply never reports one.
   */
  private static boolean isGainmapCapableContentType(@Nullable String contentType) {
    return MediaUtil.isJpegType(contentType) ||
           MediaUtil.isHeicType(contentType) ||
           MediaUtil.isHeifType(contentType);
  }
}
