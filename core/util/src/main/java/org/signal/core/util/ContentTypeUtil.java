/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util;

import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Utility methods for working with media content types.
 *
 * Intended to live in core util so feature modules can reuse common
 * MIME/type predicates without depending on app-layer utilities.
 */
public final class ContentTypeUtil {

  private ContentTypeUtil() {}

  public static final String IMAGE_PNG         = "image/png";
  public static final String IMAGE_JPEG        = "image/jpeg";
  public static final String IMAGE_HEIC        = "image/heic";
  public static final String IMAGE_HEIF        = "image/heif";
  public static final String IMAGE_AVIF        = "image/avif";
  public static final String IMAGE_WEBP        = "image/webp";
  public static final String IMAGE_GIF         = "image/gif";
  public static final String AUDIO_AAC         = "audio/aac";
  public static final String AUDIO_MP4         = "audio/mp4";
  public static final String AUDIO_UNSPECIFIED = "audio/*";
  public static final String VIDEO_MP4         = "video/mp4";
  public static final String VIDEO_UNSPECIFIED = "video/*";
  public static final String VCARD             = "text/x-vcard";
  public static final String LONG_TEXT         = "text/x-signal-plain";
  public static final String VIEW_ONCE         = "application/x-signal-view-once";
  public static final String UNKNOWN           = "*/*";
  public static final String OCTET             = "application/octet-stream";

  public static boolean isMms(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("application/mms");
  }

  public static boolean isVideo(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().startsWith("video/");
  }

  public static boolean isVcard(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(VCARD);
  }

  public static boolean isGif(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_GIF);
  }

  public static boolean isJpegType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_JPEG);
  }

  public static boolean isHeicType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_HEIC);
  }

  public static boolean isHeifType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_HEIF);
  }

  public static boolean isAvifType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_AVIF);
  }

  public static boolean isWebpType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_WEBP);
  }

  public static boolean isPngType(@Nullable String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals(IMAGE_PNG);
  }

  public static boolean isTextType(@Nullable String contentType) {
    return contentType != null && contentType.startsWith("text/");
  }

  public static boolean isImageType(@Nullable String contentType) {
    if (contentType == null) {
      return false;
    }

    return (contentType.startsWith("image/") && !contentType.equals("image/svg+xml")) ||
           contentType.equals(MediaStore.Images.Media.CONTENT_TYPE);
  }

  public static boolean isAudioType(@Nullable String contentType) {
    if (contentType == null) {
      return false;
    }

    return contentType.startsWith("audio/") ||
           contentType.equals(MediaStore.Audio.Media.CONTENT_TYPE);
  }

  public static boolean isVideoType(@Nullable String contentType) {
    if (contentType == null) {
      return false;
    }

    return contentType.startsWith("video/") ||
           contentType.equals(MediaStore.Video.Media.CONTENT_TYPE);
  }

  public static boolean isImageOrVideoType(@Nullable String contentType) {
    return isImageType(contentType) || isVideoType(contentType);
  }

  public static boolean isStorySupportedType(@Nullable String contentType) {
    return isImageOrVideoType(contentType) && !isGif(contentType);
  }

  public static boolean isImageVideoOrAudioType(@Nullable String contentType) {
    return isImageOrVideoType(contentType) || isAudioType(contentType);
  }

  public static boolean isImageAndNotGif(@Nullable String contentType) {
    return isImageType(contentType) && !isGif(contentType);
  }

  public static boolean isLongTextType(@Nullable String contentType) {
    return contentType != null && contentType.equals(LONG_TEXT);
  }

  public static boolean isViewOnceType(@Nullable String contentType) {
    return contentType != null && contentType.equals(VIEW_ONCE);
  }

  public static boolean isOctetStream(@Nullable String contentType) {
    return OCTET.equals(contentType);
  }

  public static boolean isDocumentType(@Nullable String contentType) {
    return !isImageOrVideoType(contentType) && !isGif(contentType) && !isLongTextType(contentType) && !isViewOnceType(contentType);
  }
}