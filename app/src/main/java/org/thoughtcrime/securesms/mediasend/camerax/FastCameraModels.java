package org.thoughtcrime.securesms.mediasend.camerax;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * A set of {@link android.os.Build#MODEL} that are known to both benefit from
 * {@link androidx.camera.core.ImageCapture.CaptureMode#MAX_QUALITY} and execute it quickly.
 *
 */
public class FastCameraModels {

  private static final Set<String> MODELS = new HashSet<String>() {{
    add("Pixel 2");
    add("Pixel 2 XL");
    add("Pixel 3");
    add("Pixel 3 XL");
    add("Pixel 3a");
    add("Pixel 3a XL");
  }};

  /**
   * @param model Should be a {@link android.os.Build#MODEL}.
   */
  public static boolean contains(@NonNull String model) {
    return MODELS.contains(model);
  }
}
