package org.thoughtcrime.securesms.video;

import org.thoughtcrime.securesms.video.videoconverter.utils.VideoConstants;

/**
 * Calculates a target quality output for a video to fit within a specified size.
 */
public final class VideoBitRateCalculator {

  private final long upperFileSizeLimitWithMargin;

  public VideoBitRateCalculator(long upperFileSizeLimit) {
    upperFileSizeLimitWithMargin = (long) (upperFileSizeLimit / 1.1);
  }

  /**
   * Gets the output quality of a video of the given {@param duration}.
   */
  public TranscodingQuality getTargetQuality(long duration, int inputTotalBitRate) {
    int maxVideoBitRate = Math.min(VideoConstants.VIDEO_TARGET_BIT_RATE, inputTotalBitRate - VideoConstants.AUDIO_BIT_RATE);
    int minVideoBitRate = Math.min(VideoConstants.VIDEO_MINIMUM_TARGET_BIT_RATE, maxVideoBitRate);

    int    targetVideoBitRate = Math.max(minVideoBitRate, Math.min(getTargetVideoBitRate(upperFileSizeLimitWithMargin, duration), maxVideoBitRate));
    int    bitRateRange       = maxVideoBitRate - minVideoBitRate;
    double quality            = bitRateRange == 0 ? 1 : (targetVideoBitRate - minVideoBitRate) / (double) bitRateRange;
    int    outputResolution   = targetVideoBitRate < VideoConstants.LOW_RES_TARGET_VIDEO_BITRATE ? VideoConstants.LOW_RES_OUTPUT_FORMAT : VideoConstants.VIDEO_SHORT_EDGE;

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

  public int getMaxVideoUploadDurationInSeconds() {
    long totalMinimumBitrate = VideoConstants.VIDEO_MINIMUM_TARGET_BIT_RATE + VideoConstants.AUDIO_BIT_RATE;
    return Math.toIntExact((upperFileSizeLimitWithMargin * 8) / totalMinimumBitrate);
  }
}
