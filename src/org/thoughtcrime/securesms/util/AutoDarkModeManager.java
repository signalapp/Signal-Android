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
  private SensorValueReader sensorValueReader;
  private WeakReference<Activity> activityReference;

  public AutoDarkModeManager(Activity activity) {
    activityReference = new WeakReference<>(activity);
    sensorValueReader = new SensorValueReader(activity, Sensor.TYPE_LIGHT, this);
  }

  public static boolean shouldShowDarkTheme() {
    return showDarkTheme;
  }

  public void startListening() {
    sensorValueReader.readSingleValue();
  }

  public void stopListening() {
    sensorValueReader.stopListening();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    float ambientLightInLux = event.values[0];

    if (shouldChangeTheme(ambientLightInLux)) {
      changeTheme(ambientLightInLux);
      recreateActivity();
    }
  }

  private boolean shouldChangeTheme(float ambientLightInLux) {
    return (lowLight(ambientLightInLux) && !showDarkTheme) || (!lowLight(ambientLightInLux) && showDarkTheme);
  }

  private boolean lowLight(float ambientLightInLux) {
    return ambientLightInLux <= MAX_LUX_FOR_DARK_THEME;
  }

  private void changeTheme(float ambientLightInLux) {
    showDarkTheme = lowLight(ambientLightInLux);
  }

  private void recreateActivity() {
    Activity activity = activityReference.get();

    if (activity != null)
      ActivityUtil.recreateActivity(activity);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }
}
