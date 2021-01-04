// Copyright 2010 Square, Inc.
// Modified 2020 Signal

package org.signal.core.util;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Detects phone shaking. If more than 75% of the samples taken in the past 0.5s are
 * accelerating, the device is a) shaking, or b) free falling 1.84m (h =
 * 1/2*g*t^2*3/4).
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Eric Burke (eric@squareup.com)
 */
public class ShakeDetector implements SensorEventListener {

  private static final int SHAKE_THRESHOLD = 13;

  /** Listens for shakes. */
  public interface Listener {
    /** Called on the main thread when the device is shaken. */
    void onShakeDetected();
  }

  private final SampleQueue queue = new SampleQueue();
  private final Listener    listener;

  private SensorManager sensorManager;
  private Sensor        accelerometer;

  public ShakeDetector(Listener listener) {
    this.listener = listener;
  }

  /**
   * Starts listening for shakes on devices with appropriate hardware.
   *
   * @return true if the device supports shake detection.
   */
  public boolean start(SensorManager sensorManager) {
    if (accelerometer != null) {
      return true;
    }

    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    if (accelerometer != null) {
      this.sensorManager = sensorManager;
      sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    return accelerometer != null;
  }

  /**
   * Stops listening. Safe to call when already stopped. Ignored on devices without appropriate
   * hardware.
   */
  public void stop() {
    if (accelerometer != null) {
      queue.clear();
      sensorManager.unregisterListener(this, accelerometer);
      sensorManager = null;
      accelerometer = null;
    }
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    boolean accelerating = isAccelerating(event);
    long    timestamp    = event.timestamp;

    queue.add(timestamp, accelerating);

    if (queue.isShaking()) {
      queue.clear();
      listener.onShakeDetected();
    }
  }

  /** Returns true if the device is currently accelerating. */
  private boolean isAccelerating(SensorEvent event) {
    float ax = event.values[0];
    float ay = event.values[1];
    float az = event.values[2];

    // Instead of comparing magnitude to ACCELERATION_THRESHOLD,
    // compare their squares. This is equivalent and doesn't need the
    // actual magnitude, which would be computed using (expensive) Math.sqrt().
    final double magnitudeSquared = ax * ax + ay * ay + az * az;

    return magnitudeSquared > SHAKE_THRESHOLD * SHAKE_THRESHOLD;
  }

  /** Queue of samples. Keeps a running average. */
  static class SampleQueue {

    /** Window size in ns. Used to compute the average. */
    private static final long MAX_WINDOW_SIZE = 500000000; // 0.5s
    private static final long MIN_WINDOW_SIZE = MAX_WINDOW_SIZE >> 1; // 0.25s

    /**
     * Ensure the queue size never falls below this size, even if the device
     * fails to deliver this many events during the time window. The LG Ally
     * is one such device.
     */
    private static final int MIN_QUEUE_SIZE = 4;

    private final SamplePool pool = new SamplePool();

    private Sample oldest;
    private Sample newest;
    private int    sampleCount;
    private int    acceleratingCount;

    /**
     * Adds a sample.
     *
     * @param timestamp    in nanoseconds of sample
     * @param accelerating true if > {@link #SHAKE_THRESHOLD}.
     */
    void add(long timestamp, boolean accelerating) {
      purge(timestamp - MAX_WINDOW_SIZE);

      Sample added = pool.acquire();

      added.timestamp    = timestamp;
      added.accelerating = accelerating;
      added.next         = null;

      if (newest != null) {
        newest.next = added;
      }

      newest = added;

      if (oldest == null) {
        oldest = added;
      }

      sampleCount++;

      if (accelerating) {
        acceleratingCount++;
      }
    }

    /** Removes all samples from this queue. */
    void clear() {
      while (oldest != null) {
        Sample removed = oldest;
        oldest = removed.next;
        pool.release(removed);
      }

      newest            = null;
      sampleCount       = 0;
      acceleratingCount = 0;
    }

    /** Purges samples with timestamps older than cutoff. */
    void purge(long cutoff) {
      while (sampleCount >= MIN_QUEUE_SIZE && oldest != null && cutoff - oldest.timestamp > 0) {
        Sample removed = oldest;

        if (removed.accelerating) {
          acceleratingCount--;
        }

        sampleCount--;

        oldest = removed.next;

        if (oldest == null) {
          newest = null;
        }

        pool.release(removed);
      }
    }

    /**
     * Returns true if we have enough samples and more than 3/4 of those samples
     * are accelerating.
     */
    boolean isShaking() {
      return newest != null                                         &&
             oldest != null                                         &&
             newest.timestamp - oldest.timestamp >= MIN_WINDOW_SIZE &&
             acceleratingCount >= (sampleCount >> 1) + (sampleCount >> 2);
    }
  }

  /** An accelerometer sample. */
  static class Sample {
    /** Time sample was taken. */
    long timestamp;

    /** If acceleration > {@link #SHAKE_THRESHOLD}. */
    boolean accelerating;

    /** Next sample in the queue or pool. */
    Sample next;
  }

  /** Pools samples. Avoids garbage collection. */
  static class SamplePool {
    private Sample head;

    /** Acquires a sample from the pool. */
    Sample acquire() {
      Sample acquired = head;

      if (acquired == null) {
        acquired = new Sample();
      } else {
        head = acquired.next;
      }

      return acquired;
    }

    /** Returns a sample to the pool. */
    void release(Sample sample) {
      sample.next = head;
      head = sample;
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }
}

