/*
 * Copyright (C) 2017 Fernando Garcia Alvarez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Class used to manage the automatic
 * dark mode based on ambient light
 *
 * @author fercarcedo
 */
public class AutoDarkModeManager implements SensorEventListener {
  private static final float MAX_LUX_FOR_DARK_THEME = 10;
  private static boolean showDarkTheme;
  private static String lastActivityName;

  private Activity activity;

  public AutoDarkModeManager(Activity activity) {
    this.activity = activity;
  }

  public static boolean shouldShowDarkTheme() {
    return showDarkTheme;
  }

  public void listenForCurrentActivityIfNecessary() {
    if (!activity.getClass().getSimpleName().equals(lastActivityName)) {
      startListening();
    }
  }

  private void startListening() {
    SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

    if (sensor != null) {
      sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  private void stopListening() {
    SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    sensorManager.unregisterListener(this);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    lastActivityName = activity.getClass().getSimpleName();

    float lux = event.values[0];
    boolean previousShowDarkTheme = showDarkTheme;
    showDarkTheme = lux <= MAX_LUX_FOR_DARK_THEME;
    if (previousShowDarkTheme != showDarkTheme)
      ActivityUtil.recreateActivity(activity);
    stopListening();
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }
}
