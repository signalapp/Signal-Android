package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.NonNull;

public final class VibrateUtil {

  private static final int TICK_LENGTH = 30;

  private VibrateUtil() { }

  public static void vibrateTick(@NonNull Context context) {
    vibrate(context, TICK_LENGTH);
  }

  public static void vibrate(@NonNull Context context, int duration) {
    Vibrator vibrator = ServiceUtil.getVibrator(context);

    if (Build.VERSION.SDK_INT >= 26) {
      VibrationEffect effect = VibrationEffect.createOneShot(duration, 64);
      vibrator.vibrate(effect);
    } else {
      vibrator.vibrate(duration);
    }
  }

  public static boolean isHapticFeedbackEnabled(@NonNull Context context) {
      int enabled = Settings.System.getInt(context.getContentResolver(),
                                           android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
      return enabled != 0;
  }
}
