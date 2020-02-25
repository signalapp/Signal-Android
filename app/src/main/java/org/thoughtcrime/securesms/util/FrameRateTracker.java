package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.view.Choreographer;
import android.view.Display;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Tracks the frame rate of the app and logs when things are bad.
 *
 * In general, whenever alterations are made here, the author should be very cautious to do as
 * little work as possible, because we don't want the tracker itself to impact the frame rate.
 */
public class FrameRateTracker {

  private static final String TAG = Log.tag(FrameRateTracker.class);

  private static final long REPORTING_INTERVAL = TimeUnit.SECONDS.toMillis(1);

  private static final int MAX_CONSECUTIVE_FRAME_LOGS    = 10;
  private static final int MAX_CONSECUTIVE_INTERVAL_LOGS = 10;

  private final Context      context;
  private final List<Double> fpsData;
  private final RingBuffer   runningAverageFps;

  private double refreshRate;
  private long   idealTimePerFrameNanos;
  private long   badFrameThresholdNanos;
  private double badIntervalThresholdFps;

  private long lastFrameTimeNanos;
  private long lastReportTimeNanos;

  private long consecutiveFrameWarnings;
  private long consecutiveIntervalWarnings;

  public FrameRateTracker(@NonNull Context context) {
    this.context                 = context;
    this.fpsData                 = new ArrayList<>();
    this.runningAverageFps       = new RingBuffer(TimeUnit.SECONDS.toMillis(10));

    updateRefreshRate();
  }

  public void begin() {
    Log.d(TAG, String.format(Locale.ENGLISH, "Beginning frame rate tracking. Screen refresh rate: %.2f hz, or %.2f ms per frame.", refreshRate, idealTimePerFrameNanos / (float) 1_000_000));

    lastFrameTimeNanos  = System.nanoTime();
    lastReportTimeNanos = System.nanoTime();

    Choreographer.getInstance().postFrameCallback(calculator);
    Choreographer.getInstance().postFrameCallbackDelayed(reporter, 1000);
  }

  public void end() {
    Choreographer.getInstance().removeFrameCallback(calculator);
    Choreographer.getInstance().removeFrameCallback(reporter);

    fpsData.clear();
    runningAverageFps.clear();
  }

  public double getRunningAverageFps() {
    return runningAverageFps.getAverage();
  }

  /**
   * The natural screen refresh rate, in hertz. May not always return the same value if a display
   * has a dynamic refresh rate.
   */
  public static float getDisplayRefreshRate(@NonNull Context context) {
    Display display = ServiceUtil.getWindowManager(context).getDefaultDisplay();
    return display.getRefreshRate();
  }

  /**
   * Displays with dynamic refresh rates may change their reported refresh rate over time.
   */
  private void updateRefreshRate() {
    double newRefreshRate = getDisplayRefreshRate(context);

    if (this.refreshRate != newRefreshRate) {
      if (this.refreshRate > 0) {
        Log.d(TAG, String.format(Locale.ENGLISH, "Refresh rate changed from %.2f hz to %.2f hz", refreshRate, newRefreshRate));
      }

      this.refreshRate             = getDisplayRefreshRate(context);
      this.idealTimePerFrameNanos  = (long) (TimeUnit.SECONDS.toNanos(1) / refreshRate);
      this.badFrameThresholdNanos  = idealTimePerFrameNanos * (int) (refreshRate / 4);
      this.badIntervalThresholdFps = refreshRate /  2;
    }
  }

  private final Choreographer.FrameCallback calculator = new Choreographer.FrameCallback() {
    @Override
    public void doFrame(long frameTimeNanos) {
      long   elapsedNanos = frameTimeNanos - lastFrameTimeNanos;
      double fps          = TimeUnit.SECONDS.toNanos(1) / (double) elapsedNanos;

      if (elapsedNanos > badFrameThresholdNanos) {
        if (consecutiveFrameWarnings < MAX_CONSECUTIVE_FRAME_LOGS) {
          long droppedFrames = elapsedNanos / idealTimePerFrameNanos;
          Log.w(TAG, String.format(Locale.ENGLISH, "Bad frame! Took %d ms (%d dropped frames, or %.2f FPS)", TimeUnit.NANOSECONDS.toMillis(elapsedNanos), droppedFrames, fps));
          consecutiveFrameWarnings++;
        }
      } else {
        consecutiveFrameWarnings = 0;
      }

      fpsData.add(fps);
      runningAverageFps.add(fps);

      lastFrameTimeNanos = frameTimeNanos;
      Choreographer.getInstance().postFrameCallback(this);
    }
  };

  private final Choreographer.FrameCallback reporter = new Choreographer.FrameCallback() {
    @Override
    public void doFrame(long frameTimeNanos) {
      double averageFps = 0;
      int    size       = fpsData.size();

      for (double fps : fpsData) {
        averageFps += fps / size;
      }

      if (averageFps < badIntervalThresholdFps) {
        if (consecutiveIntervalWarnings < MAX_CONSECUTIVE_INTERVAL_LOGS) {
          Log.w(TAG, String.format(Locale.ENGLISH, "Bad interval! Average of %.2f FPS over the last %d ms", averageFps, TimeUnit.NANOSECONDS.toMillis(frameTimeNanos - lastReportTimeNanos)));
          consecutiveIntervalWarnings++;
        }
      } else {
        consecutiveIntervalWarnings = 0;
      }

      lastReportTimeNanos = frameTimeNanos;
      updateRefreshRate();
      Choreographer.getInstance().postFrameCallbackDelayed(this, REPORTING_INTERVAL);
    }
  };

  private static class RingBuffer {
    private final long               interval;
    private final ArrayDeque<Long>   timestamps;
    private final ArrayDeque<Double> elements;

    RingBuffer(long interval) {
      this.interval   = interval;
      this.timestamps = new ArrayDeque<>();
      this.elements   = new ArrayDeque<>();
    }

    void add(double value) {
      long currentTime = System.currentTimeMillis();

      while (!timestamps.isEmpty() && timestamps.getFirst() < (currentTime - interval)) {
        timestamps.pollFirst();
        elements.pollFirst();
      }

      timestamps.addLast(currentTime);
      elements.addLast(value);
    }

    double getAverage() {
      List<Double> elementsCopy = new ArrayList<>(elements);
      double       average      = 0;
      int          size         = elementsCopy.size();

      for (double element : elementsCopy) {
        average += element / size;
      }

      return average;
    }

    void clear() {
      timestamps.clear();
      elements.clear();
    }
  }
}
