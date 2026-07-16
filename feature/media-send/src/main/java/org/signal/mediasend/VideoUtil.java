/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.video.TranscodingConfig;
import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants;

public final class VideoUtil {

  private VideoUtil() { }

  public static int getMaxVideoRecordDurationInSeconds(@NonNull MediaConstraints mediaConstraints) {
    TranscodingConfig.QualityTier config      = VideoConstants.getDEFAULT_HIGH();
    int                           maxBytes    = (int) (config.getVideoBitrateMbps() * VideoConstants.MB) / 8 + (config.getAudioBitrateKbps() * VideoConstants.KB) / 8;
    long                          allowedSize = mediaConstraints.getCompressedVideoMaxSize();
    int                           duration    = (int) Math.floor((float) allowedSize / maxBytes);

    return Math.min(duration, VideoConstants.VIDEO_MAX_RECORD_LENGTH_S);
  }
}
