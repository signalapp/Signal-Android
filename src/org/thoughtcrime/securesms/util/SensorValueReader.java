package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Class used to simplify reading values
 * from sensors
 *
 * @author fercarcedo
 */
public class SensorValueReader implements SensorEventListener {

  private SensorEventListener listener;
  private SensorManager sensorManager;
  private int sensorCode;

  public SensorValueReader(Context context, int sensorCode, SensorEventListener listener) {
    this.listener = listener;
    this.sensorManager = getSensorManager(context);
    this.sensorCode = sensorCode;
  }

  public void readSingleValue() {
    registerSingleValueListener();
  }

  private void registerSingleValueListener() {
    Sensor sensor = sensorManager.getDefaultSensor(sensorCode);

    if (sensor != null) {
      sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }
  }

  private SensorManager getSensorManager(Context context) {
    return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
  }

  public void stopListening() {
    stopListening(this);
  }

  private void stopListening(SensorEventListener listener) {
    sensorManager.unregisterListener(listener);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    listener.onSensorChanged(event);
    stopListening(this);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    listener.onAccuracyChanged(sensor, accuracy);
  }
}
