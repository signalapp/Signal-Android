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
   * Whether we should install gain-map-aware decoders for this attachment. This is a cheap
   * pre-filter only; the authoritative answer comes from {@link #hasGainmap(Bitmap)} on the
   * decoded bitmap.
   * <p>
   * This is the feature floor for the whole HDR preview, and it is API 35 rather than 34 because that is where
   * {@code Window#setDesiredHdrHeadroom} arrives. Without it the compositor picks the headroom itself and
   * visibly dims the SDR chrome (toolbar, caption, album rail) sharing the preview window, so on API 34 there
   * is nothing worth detecting a gain map for.
   */
  public static boolean isEligible(@NonNull Uri uri, @Nullable String contentType) {
    return Build.VERSION.SDK_INT >= 35 &&
           isGainmapCapableContentType(contentType) &&
           PartAuthority.isLocalUri(uri);
  }

  /**
   * Whether the provided bitmap carries an UltraHDR gain map.
   * <p>
   * The API 34 guard here is {@link Bitmap#hasGainmap()}'s own availability floor, not the preview's feature
   * floor -- callers only reach this after {@link #isEligible(Uri, String)} has already applied the latter.
   */
  public static boolean hasGainmap(@Nullable Bitmap bitmap) {
    return Build.VERSION.SDK_INT >= 34 && bitmap != null && bitmap.hasGainmap();
  }

  /**
   * Containers that can carry a gain map. This is deliberately wider than what every platform version can
   * actually parse -- JPEG is the only one guaranteed to report a gain map today, while HEIC/HEIF depends on
   * the platform's codec. Listing them costs nothing (a bitmap without a gain map simply never reports) and
   * avoids a second code change when a platform gains HEIF gain map decoding.
   */
  private static boolean isGainmapCapableContentType(@Nullable String contentType) {
    return MediaUtil.isJpegType(contentType) ||
           MediaUtil.isHeicType(contentType) ||
           MediaUtil.isHeifType(contentType);
  }
}
