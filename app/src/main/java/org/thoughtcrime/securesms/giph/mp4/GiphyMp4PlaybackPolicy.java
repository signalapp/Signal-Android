package org.thoughtcrime.securesms.giph.mp4;

import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.MimeTypes;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.FeatureFlags;

import java.util.concurrent.TimeUnit;

/**
 * Central policy object for determining what kind of gifs to display, routing, etc.
 */
public final class GiphyMp4PlaybackPolicy {

  private static final int   MAXIMUM_SUPPORTED_PLAYBACK_PRE_23         = 6;
  private static final int   MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM = 3;
  private static final float SEARCH_RESULT_RATIO                       = 0.75f;

  private GiphyMp4PlaybackPolicy() { }

  public static boolean sendAsMp4() {
    return FeatureFlags.mp4GifSendSupport();
  }

  public static boolean autoplay() {
    return !DeviceProperties.isLowMemoryDevice(ApplicationDependencies.getApplication());
  }

  public static int maxRepeatsOfSinglePlayback() {
    return 4;
  }

  public static long maxDurationOfSinglePlayback() {
    return TimeUnit.SECONDS.toMillis(8);
  }

  public static int maxSimultaneousPlaybackInConversation() {
    return maxSimultaneousPlaybackWithRatio(1f - SEARCH_RESULT_RATIO);
  }

  public static int maxSimultaneousPlaybackInSearchResults() {
    return maxSimultaneousPlaybackWithRatio(SEARCH_RESULT_RATIO);
  }

  private static int maxSimultaneousPlaybackWithRatio(float ratio) {
    int maxInstances = 0;

    try {
      MediaCodecInfo info = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false);

      if (info != null && info.getMaxSupportedInstances() > 0) {
        maxInstances = (int) (info.getMaxSupportedInstances() * ratio);
      }

    } catch (MediaCodecUtil.DecoderQueryException ignored) {
    }

    if (maxInstances > 0) {
      return maxInstances;
    }

    if (DeviceProperties.isLowMemoryDevice(ApplicationDependencies.getApplication())) {
      return (int) (MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM * ratio);
    } else {
      return (int) (MAXIMUM_SUPPORTED_PLAYBACK_PRE_23 * ratio);
    }
  }
}
