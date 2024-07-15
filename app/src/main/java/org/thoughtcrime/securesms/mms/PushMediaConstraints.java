package org.thoughtcrime.securesms.mms;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.LocaleRemoteConfig;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.video.TranscodingPreset;

import java.util.Arrays;

public class PushMediaConstraints extends MediaConstraints {

  private static final int KB = 1024;
  private static final int MB = 1024 * KB;

  private final MediaConfig currentConfig;

  public PushMediaConstraints(@Nullable SentMediaQuality sentMediaQuality) {
    currentConfig = getCurrentConfig(AppDependencies.getApplication(), sentMediaQuality);
  }

  @Override
  public boolean isHighQuality() {
    return currentConfig == MediaConfig.LEVEL_3;
  }

  @Override
  public int getImageMaxWidth(Context context) {
    return currentConfig.imageSizeTargets[0];
  }

  @Override
  public int getImageMaxHeight(Context context) {
    return getImageMaxWidth(context);
  }

  @Override
  public int getImageMaxSize(Context context) {
    return (int) Math.min(currentConfig.maxImageFileSize, getMaxAttachmentSize());
  }

  @Override
  public int[] getImageDimensionTargets(Context context) {
    return currentConfig.imageSizeTargets;
  }

  @Override
  public long getGifMaxSize(Context context) {
    return Math.min(25 * MB, getMaxAttachmentSize());
  }

  @Override
  public long getVideoMaxSize(Context context) {
    return getMaxAttachmentSize();
  }

  @Override
  public long getUncompressedVideoMaxSize(Context context) {
    return isVideoTranscodeAvailable() ? RemoteConfig.maxSourceTranscodeVideoSizeBytes()
                                       : getVideoMaxSize(context);
  }

  @Override
  public long getCompressedVideoMaxSize(Context context) {
    if (RemoteConfig.useStreamingVideoMuxer()) {
      return getMaxAttachmentSize();
    } else {
      return Util.isLowMemory(context) ? 30 * MB
                                       : 50 * MB;
    }
  }

  @Override
  public long getAudioMaxSize(Context context) {
    return getMaxAttachmentSize();
  }

  @Override
  public long getDocumentMaxSize(Context context) {
    return getMaxAttachmentSize();
  }

  @Override
  public int getImageCompressionQualitySetting(@NonNull Context context) {
    return currentConfig.qualitySetting;
  }

  @Override
  public TranscodingPreset getVideoTranscodingSettings() {
    return currentConfig.videoPreset;
  }

  private static @NonNull MediaConfig getCurrentConfig(@NonNull Context context, @Nullable SentMediaQuality sentMediaQuality) {
    if (Util.isLowMemory(context)) {
      return MediaConfig.LEVEL_1_LOW_MEMORY;
    }

    if (sentMediaQuality == SentMediaQuality.HIGH) {
      return MediaConfig.LEVEL_3;
    }
    return LocaleRemoteConfig.getMediaQualityLevel().orElse(MediaConfig.getDefault(context));
  }

  public enum MediaConfig {
    LEVEL_1_LOW_MEMORY(true, 1, MB, new int[] { 768, 512 }, 70, TranscodingPreset.LEVEL_1),

    LEVEL_1(false, 1, MB, new int[] { 1600, 1024, 768, 512 }, 70, TranscodingPreset.LEVEL_1),
    LEVEL_2(false, 2, (int) (1.5 * MB), new int[] { 2048, 1600, 1024, 768, 512 }, 75, TranscodingPreset.LEVEL_2),
    LEVEL_3(false, 3, (int) (3 * MB), new int[] { 4096, 3072, 2048, 1600, 1024, 768, 512 }, 75, TranscodingPreset.LEVEL_3);

    private final boolean           isLowMemory;
    private final int               level;
    private final int               maxImageFileSize;
    private final int[]             imageSizeTargets;
    private final int               qualitySetting;
    private final TranscodingPreset videoPreset;

    MediaConfig(boolean isLowMemory,
                int level,
                int maxImageFileSize,
                @NonNull int[] imageSizeTargets,
                @IntRange(from = 0, to = 100) int qualitySetting,
                TranscodingPreset videoPreset)
    {
      this.isLowMemory      = isLowMemory;
      this.level            = level;
      this.maxImageFileSize = maxImageFileSize;
      this.imageSizeTargets = imageSizeTargets;
      this.qualitySetting   = qualitySetting;
      this.videoPreset      = videoPreset;
    }

    public int getMaxImageFileSize() {
      return maxImageFileSize;
    }

    public int[] getImageSizeTargets() {
      return imageSizeTargets;
    }

    public int getImageQualitySetting() {
      return qualitySetting;
    }

    public TranscodingPreset getVideoPreset() {
      return videoPreset;
    }

    public static @Nullable MediaConfig forLevel(int level) {
      boolean isLowMemory = Util.isLowMemory(AppDependencies.getApplication());

      return Arrays.stream(values())
                   .filter(v -> v.level == level && v.isLowMemory == isLowMemory)
                   .findFirst()
                   .orElse(null);
    }

    public static @NonNull MediaConfig getDefault(Context context) {
      return Util.isLowMemory(context) ? LEVEL_1_LOW_MEMORY : LEVEL_1;
    }
  }
}
