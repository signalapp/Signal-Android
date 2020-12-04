package org.thoughtcrime.securesms.video;

/**
 * Calculates a target quality output for a video to fit within a specified size.
 */
public final class VideoBitRateCalculator {

  private static final int MAXIMUM_TARGET_VIDEO_BITRATE = VideoUtil.VIDEO_BIT_RATE;
  private static final int LOW_RES_TARGET_VIDEO_BITRATE = 1_750_000;
  private static final int MINIMUM_TARGET_VIDEO_BITRATE = 500_000;
  private static final int AUDIO_BITRATE                = VideoUtil.AUDIO_BIT_RATE;
  private static final int OUTPUT_FORMAT                = VideoUtil.VIDEO_SHORT_WIDTH;
  private static final int LOW_RES_OUTPUT_FORMAT        = 480;

  private final long upperFileSizeLimitWithMargin;

  public VideoBitRateCalculator(long upperFileSizeLimit) {
    upperFileSizeLimitWithMargin = (long) (upperFileSizeLimit / 1.1);
  }

  /**
   * Gets the output quality of a video of the given {@param duration}.
   */
  public Quality getTargetQuality(long duration, int inputTotalBitRate) {
    int maxVideoBitRate = Math.min(MAXIMUM_TARGET_VIDEO_BITRATE, inputTotalBitRate - AUDIO_BITRATE);
    int minVideoBitRate = Math.min(MINIMUM_TARGET_VIDEO_BITRATE, maxVideoBitRate);

    int    targetVideoBitRate = Math.max(minVideoBitRate, Math.min(getTargetVideoBitRate(upperFileSizeLimitWithMargin, duration), maxVideoBitRate));
    int    bitRateRange       = maxVideoBitRate - minVideoBitRate;
    double quality            = bitRateRange == 0 ? 1 : (targetVideoBitRate - minVideoBitRate) / (double) bitRateRange;

    return new Quality(targetVideoBitRate, AUDIO_BITRATE, quality, duration);
  }

  private int getTargetVideoBitRate(long sizeGuideBytes, long duration) {
    double durationSeconds = duration / 1000d;

    sizeGuideBytes -= durationSeconds * AUDIO_BITRATE / 8;

    double targetAttachmentSizeBits = sizeGuideBytes * 8L;

    return (int) (targetAttachmentSizeBits / durationSeconds);
  }

  public static int bitRate(long bytes, long durationMs) {
    return (int) (bytes * 8 / (durationMs / 1000f));
  }

  public static class Quality {
    private final int    targetVideoBitRate;
    private final int    targetAudioBitRate;
    private final double quality;
    private final long   duration;

    private Quality(int targetVideoBitRate, int targetAudioBitRate, double quality, long duration) {
      this.targetVideoBitRate = targetVideoBitRate;
      this.targetAudioBitRate = targetAudioBitRate;
      this.quality            = Math.max(0, Math.min(quality, 1));
      this.duration           = duration;
    }

    /**
     * [0..1]
     * <p>
     * 0 = {@link #MINIMUM_TARGET_VIDEO_BITRATE}
     * 1 = {@link #MAXIMUM_TARGET_VIDEO_BITRATE}
     */
    public double getQuality() {
      return quality;
    }

    public int getTargetVideoBitRate() {
      return targetVideoBitRate;
    }

    public int getTargetAudioBitRate() {
      return targetAudioBitRate;
    }

    public int getTargetTotalBitRate() {
      return targetVideoBitRate + targetAudioBitRate;
    }

    public boolean useLowRes() {
      return targetVideoBitRate < LOW_RES_TARGET_VIDEO_BITRATE;
    }

    public int getOutputResolution() {
      return useLowRes() ? LOW_RES_OUTPUT_FORMAT
                         : OUTPUT_FORMAT;
    }

    public long getFileSizeEstimate() {
      return getTargetTotalBitRate() * duration / 8000;
    }

    @Override
    public String toString() {
      return "Quality{" +
             "targetVideoBitRate=" + targetVideoBitRate +
             ", targetAudioBitRate=" + targetAudioBitRate +
             ", quality=" + quality +
             ", duration=" + duration +
             ", filesize=" + getFileSizeEstimate() +
             '}';
    }
  }
}
