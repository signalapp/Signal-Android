package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.content.Context;
import android.view.Choreographer;
import android.view.Display;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

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

  private static final int MAX_CONSECUTIVE_FRAME_LOGS = 10;

  private final Application context;

  private double refreshRate;
  private long   idealTimePerFrameNanos;
  private long   badFrameThresholdNanos;

  private long lastFrameTimeNanos;

  private long consecutiveFrameWarnings;

  public FrameRateTracker(@NonNull Application application) {
    this.context = application;

    updateRefreshRate();
  }

  public void begin() {
    Log.d(TAG, String.format(Locale.ENGLISH, "Beginning frame rate tracking. Screen refresh rate: %.2f hz, or %.2f ms per frame.", refreshRate, idealTimePerFrameNanos / (float) 1_000_000));

    lastFrameTimeNanos  = System.nanoTime();

    Choreographer.getInstance().postFrameCallback(calculator);
  }

  public void end() {
    Choreographer.getInstance().removeFrameCallback(calculator);
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

      lastFrameTimeNanos = frameTimeNanos;
      Choreographer.getInstance().postFrameCallback(this);
    }
  };
}
