package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

/**
 * Observes the charging state and low battery state of the device and notifies the JobManager system when appropriate.
 */
public class ChargingAndBatteryIsNotLowConstraintObserver implements ConstraintObserver {

  private static final String REASON            = Log.tag(ChargingAndBatteryIsNotLowConstraintObserver.class);
  private static final int    STATUS_BATTERY    = 0;
  private static final int    LOW_BATTERY_LEVEL = 20;

  private final Application application;

  private static volatile boolean charging;
  private static volatile boolean batteryNotLow;

  public ChargingAndBatteryIsNotLowConstraintObserver(@NonNull Application application) {
    this.application = application;
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    Intent intent = application.registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        boolean wasCharging      = charging;
        boolean wasBatteryNotLow = batteryNotLow;

        charging      = isCharging(intent);
        batteryNotLow = isBatteryNotLow(intent);

        if ((charging && !wasCharging) || (batteryNotLow && !wasBatteryNotLow)) {
          notifier.onConstraintMet(REASON);
        }
      }
    }, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

    charging = isCharging(intent);
  }

  public static boolean isCharging() {
    return charging;
  }

  public static boolean isBatteryNotLow() {
    return batteryNotLow;
  }

  private static boolean isCharging(@Nullable Intent intent) {
    if (intent == null) {
      return false;
    }

    int status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, STATUS_BATTERY);
    return status != STATUS_BATTERY;
  }

  private static boolean isBatteryNotLow(@Nullable Intent intent) {
    if (intent == null) {
      return false;
    }

    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

    if (level <= 0 || scale <= 0) {
      return false;
    }

    return ((int) Math.floor(level * 100 / (double) scale)) > LOW_BATTERY_LEVEL;
  }
}
