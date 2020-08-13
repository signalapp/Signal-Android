package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;

public final class VibrateUtil {

  private static final int TICK_LENGTH = 30;

  private VibrateUtil() { }

  public static void vibrateTick(@NonNull Context context) {
    Vibrator vibrator = ServiceUtil.getVibrator(context);

    if (Build.VERSION.SDK_INT >= 26) {
      VibrationEffect effect = VibrationEffect.createOneShot(TICK_LENGTH, 64);
      vibrator.vibrate(effect);
    } else {
      vibrator.vibrate(TICK_LENGTH);
    }
  }
}
