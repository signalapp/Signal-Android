package org.thoughtcrime.securesms.giph.mp4;

import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.MimeTypes;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.concurrent.TimeUnit;

/**
 * Central policy object for determining what kind of gifs to display, routing, etc.
 */
public final class GiphyMp4PlaybackPolicy {

  private GiphyMp4PlaybackPolicy() { }

  public static boolean sendAsMp4() {
    return FeatureFlags.mp4GifSendSupport();
  }

  public static int maxRepeatsOfSinglePlayback() {
    return 3;
  }

  public static long maxDurationOfSinglePlayback() {
    return TimeUnit.SECONDS.toMillis(6);
  }

  public static int maxSimultaneousPlaybackInSearchResults() {
    int maxInstances = 0;

    try {
      MediaCodecInfo info = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false);

      if (info != null) {
        maxInstances = (int) (info.getMaxSupportedInstances() * 0.75f);
      }

    } catch (MediaCodecUtil.DecoderQueryException ignored) {
    }

    if (maxInstances > 0) {
      return maxInstances;
    }

    if (DeviceProperties.isLowMemoryDevice(ApplicationDependencies.getApplication())) {
      return 2;
    } else {
      return 6;
    }
  }
}
