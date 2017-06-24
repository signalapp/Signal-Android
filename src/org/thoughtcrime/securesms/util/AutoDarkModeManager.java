package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.lang.ref.WeakReference;

/**
 * Class used to manage the automatic
 * dark mode based on ambient light
 *
 * @author fercarcedo
 */
public class AutoDarkModeManager implements SensorEventListener {
  private static final float MAX_LUX_FOR_DARK_THEME = 10;
  private static boolean showDarkTheme;
  private static Class<?> lastActivityClass;
  private static SensorManager sensorManager;
  private static boolean luxValueFound;
  private WeakReference<Activity> activity;

  public static boolean shouldShowDarkTheme() {
    return showDarkTheme;
  }

  public static void listenForCurrentActivityIfNecessary(Activity activity) {
    if (activity.getClass() != lastActivityClass || !luxValueFound) {
      luxValueFound = false;
      new AutoDarkModeManager().startListening(activity);
    }
  }

  private void startListening(Activity activity) {
    this.activity = new WeakReference<>(activity);
    lastActivityClass = activity.getClass();
    sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

    if (sensor != null) {
      sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  private void stopListening() {
    sensorManager.unregisterListener(this);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    parseAmbientLightValue(event);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }

  private synchronized void parseAmbientLightValue(SensorEvent event) {
    luxValueFound = true;
    float lux = event.values[0];
    boolean previousShowDarkTheme = showDarkTheme;
    showDarkTheme = lux <= MAX_LUX_FOR_DARK_THEME;
    Activity currentActivity = activity.get();
    if (previousShowDarkTheme != showDarkTheme && currentActivity != null)
      ActivityUtil.recreateActivity(currentActivity);
    stopListening();
  }
}
