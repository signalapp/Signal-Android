package org.thoughtcrime.securesms.mediasend;

import android.os.Build;

import java.util.HashSet;
import java.util.Set;

public final class LegacyCameraModels {
  private static final Set<String> LEGACY_MODELS = new HashSet<String>() {{
    add("Pixel 4");
    add("Pixel 4 XL");
  }};

  private LegacyCameraModels() {
  }

  public static boolean isLegacyCameraModel() {
    return LEGACY_MODELS.contains(Build.MODEL);
  }
}
