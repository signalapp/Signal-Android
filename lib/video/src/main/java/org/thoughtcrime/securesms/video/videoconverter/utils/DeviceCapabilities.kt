/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter.utils

import android.media.MediaCodecList
import android.media.MediaFormat
import org.signal.core.util.isNotNullOrBlank

object DeviceCapabilities {
  @JvmStatic
  fun canEncodeHevc(): Boolean {
    val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val encoder = mediaCodecList.findEncoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, VideoConstants.VIDEO_LONG_EDGE_HD, VideoConstants.VIDEO_SHORT_EDGE_HD))
    return encoder.isNotNullOrBlank()
  }
}
