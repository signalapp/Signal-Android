package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class to get density information about a device's display
 */
public final class ScreenDensity {

  private static final String UNKNOWN = "unknown";

  private static final float XHDPI_TO_LDPI = 0.25f;
  private static final float XHDPI_TO_MDPI = 0.5f;
  private static final float XHDPI_TO_HDPI = 0.75f;

  private static final LinkedHashMap<Integer, String> LEVELS = new LinkedHashMap<Integer, String>() {{
    put(DisplayMetrics.DENSITY_LOW, "ldpi");
    put(DisplayMetrics.DENSITY_MEDIUM, "mdpi");
    put(DisplayMetrics.DENSITY_HIGH, "hdpi");
    put(DisplayMetrics.DENSITY_XHIGH, "xhdpi");
    put(DisplayMetrics.DENSITY_XXHIGH, "xxhdpi");
    put(DisplayMetrics.DENSITY_XXXHIGH, "xxxhdpi");
  }};

  private final String bucket;
  private final int    density;

  public ScreenDensity(String bucket, int density) {
    this.bucket  = bucket;
    this.density = density;
  }

  public static @NonNull ScreenDensity get(@NonNull Context context) {
    int density = context.getResources().getDisplayMetrics().densityDpi;

    String bucket = UNKNOWN;

    for (Map.Entry<Integer, String> entry : LEVELS.entrySet()) {
      bucket = entry.getValue();
      if (entry.getKey() > density) {
        break;
      }
    }

    return new ScreenDensity(bucket, density);
  }

  public static @NonNull String getBestDensityBucketForDevice() {
    ScreenDensity density = get(AppDependencies.getApplication());

    if (density.isKnownDensity()) {
      return density.bucket;
    } else {
      return "xhdpi";
    }
  }

  public String getBucket() {
    return bucket;
  }

  public boolean isKnownDensity() {
    return !bucket.equals(UNKNOWN);
  }

  @Override
  public @NonNull String toString() {
    return bucket + " (" + density + ")";
  }

  public static float xhdpiRelativeDensityScaleFactor(@NonNull String density) {
    switch (density) {
      case "ldpi":
        return XHDPI_TO_LDPI;
      case "mdpi":
        return XHDPI_TO_MDPI;
      case "hdpi":
        return XHDPI_TO_HDPI;
      case "xhdpi":
        return 1f;
      default:
        throw new IllegalStateException("Unsupported density: " + density);
    }

  }
}
