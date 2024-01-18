package org.thoughtcrime.securesms.video;

import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants;

/**
 * Calculates a target quality output for a video to fit within a specified size.
 */
public final class VideoBitRateCalculator {

  private static final int MAXIMUM_TARGET_VIDEO_BITRATE = VideoConstants.VIDEO_BIT_RATE;
  private static final int LOW_RES_TARGET_VIDEO_BITRATE = 1_750_000;
  private static final int MINIMUM_TARGET_VIDEO_BITRATE = 500_000;
  private static final int LOW_RES_OUTPUT_FORMAT        = 480;

  private final long upperFileSizeLimitWithMargin;

  public VideoBitRateCalculator(long upperFileSizeLimit) {
    upperFileSizeLimitWithMargin = (long) (upperFileSizeLimit / 1.1);
  }

  /**
   * Gets the output quality of a video of the given {@param duration}.
   */
  public TranscodingQuality getTargetQuality(long duration, int inputTotalBitRate) {
    int maxVideoBitRate = Math.min(MAXIMUM_TARGET_VIDEO_BITRATE, inputTotalBitRate - VideoConstants.AUDIO_BIT_RATE);
    int minVideoBitRate = Math.min(MINIMUM_TARGET_VIDEO_BITRATE, maxVideoBitRate);

    int    targetVideoBitRate = Math.max(minVideoBitRate, Math.min(getTargetVideoBitRate(upperFileSizeLimitWithMargin, duration), maxVideoBitRate));
    int    bitRateRange       = maxVideoBitRate - minVideoBitRate;
    double quality            = bitRateRange == 0 ? 1 : (targetVideoBitRate - minVideoBitRate) / (double) bitRateRange;
    int    outputResolution   = targetVideoBitRate < LOW_RES_TARGET_VIDEO_BITRATE ? LOW_RES_OUTPUT_FORMAT : VideoConstants.VIDEO_SHORT_EDGE;

    return new TranscodingQuality(targetVideoBitRate, VideoConstants.AUDIO_BIT_RATE, Math.max(0, Math.min(quality, 1)), duration, outputResolution);
  }

  private int getTargetVideoBitRate(long sizeGuideBytes, long duration) {
    double durationSeconds = duration / 1000d;

    sizeGuideBytes -= durationSeconds * VideoConstants.AUDIO_BIT_RATE / 8;

    double targetAttachmentSizeBits = sizeGuideBytes * 8L;

    return (int) (targetAttachmentSizeBits / durationSeconds);
  }

  public static int bitRate(long bytes, long durationMs) {
    return (int) (bytes * 8 / (durationMs / 1000f));
  }

}
