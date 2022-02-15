package org.thoughtcrime.securesms.components.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import org.thoughtcrime.securesms.util.ServiceUtil;

public final class DeviceOrientationMonitor implements DefaultLifecycleObserver {

  private static final float MAGNITUDE_MAXIMUM       = 1.5f;
  private static final float MAGNITUDE_MINIMUM       = 0.75f;
  private static final float LANDSCAPE_PITCH_MINIMUM = -0.5f;
  private static final float LANDSCAPE_PITCH_MAXIMUM = 0.5f;

  private final SensorManager sensorManager;
  private final EventListener eventListener = new EventListener();

  private final float[] accelerometerReading = new float[3];
  private final float[] magnetometerReading  = new float[3];

  private final float[] rotationMatrix    = new float[9];
  private final float[] orientationAngles = new float[3];

  private final MutableLiveData<Orientation> orientation = new MutableLiveData<>(Orientation.PORTRAIT_BOTTOM_EDGE);

  public DeviceOrientationMonitor(@NonNull Context context) {
    this.sensorManager = ServiceUtil.getSensorManager(context);
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accelerometer != null) {
      sensorManager.registerListener(eventListener,
                                     accelerometer,
                                     SensorManager.SENSOR_DELAY_NORMAL,
                                     SensorManager.SENSOR_DELAY_UI);
    }
    Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    if (magneticField != null) {
      sensorManager.registerListener(eventListener,
                                     magneticField,
                                     SensorManager.SENSOR_DELAY_NORMAL,
                                     SensorManager.SENSOR_DELAY_UI);
    }
  }

  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    sensorManager.unregisterListener(eventListener);
  }

  public LiveData<Orientation> getOrientation() {
    return Transformations.distinctUntilChanged(orientation);
  }

  private void updateOrientationAngles() {
    boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
    if (!success) {
      SensorUtil.getRotationMatrixWithoutMagneticSensorData(rotationMatrix, accelerometerReading);
    }
    SensorManager.getOrientation(rotationMatrix, orientationAngles);

    float pitch = orientationAngles[1];
    float roll  = orientationAngles[2];
    float mag   = (float) Math.sqrt(Math.pow(pitch, 2) + Math.pow(roll, 2));

    if (mag > MAGNITUDE_MAXIMUM || mag < MAGNITUDE_MINIMUM) {
      return;
    }

    if (pitch > LANDSCAPE_PITCH_MINIMUM && pitch < LANDSCAPE_PITCH_MAXIMUM) {
      orientation.setValue(roll > 0 ? Orientation.LANDSCAPE_RIGHT_EDGE : Orientation.LANDSCAPE_LEFT_EDGE);
    } else {
      orientation.setValue(Orientation.PORTRAIT_BOTTOM_EDGE);
    }
  }

  private final class EventListener implements SensorEventListener {

    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
      } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
      }

      updateOrientationAngles();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
  }
}
