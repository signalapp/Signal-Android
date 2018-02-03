package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.util.Util;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class ExpirationTimerView extends HourglassView {

  private long startedAt;
  private long expiresIn;

  private boolean visible = false;
  private boolean stopped = true;

  public ExpirationTimerView(Context context) {
    super(context);
  }

  public ExpirationTimerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ExpirationTimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setExpirationTime(long startedAt, long expiresIn) {
    this.startedAt = startedAt;
    this.expiresIn = expiresIn;

    setPercentage(calculateProgress(this.startedAt, this.expiresIn));
  }

  public void startAnimation() {
    synchronized (this) {
      visible = true;
      if (!stopped) return;
      else          stopped = false;
    }

    Util.runOnMainDelayed(new AnimationUpdateRunnable(this), calculateAnimationDelay(this.startedAt, this.expiresIn));
  }

  public void stopAnimation() {
    synchronized (this) {
      visible = false;
    }
  }

  private float calculateProgress(long startedAt, long expiresIn) {
    long  progressed      = System.currentTimeMillis() - startedAt;
    float percentComplete = (float)progressed / (float)expiresIn;

    return percentComplete * 100;
  }

  private long calculateAnimationDelay(long startedAt, long expiresIn) {
    long progressed = System.currentTimeMillis() - startedAt;
    long remaining  = expiresIn - progressed;

    if (remaining < TimeUnit.SECONDS.toMillis(30)) {
      return 50;
    } else {
      return 1000;
    }
  }

  private static class AnimationUpdateRunnable implements Runnable {

    private final WeakReference<ExpirationTimerView> expirationTimerViewReference;

    private AnimationUpdateRunnable(@NonNull ExpirationTimerView expirationTimerView) {
      this.expirationTimerViewReference = new WeakReference<>(expirationTimerView);
    }

    @Override
    public void run() {
      ExpirationTimerView timerView = expirationTimerViewReference.get();
      if (timerView == null) return;

      timerView.setExpirationTime(timerView.startedAt, timerView.expiresIn);

      synchronized (timerView) {
        if (!timerView.visible) {
          timerView.stopped = true;
          return;
        }
      }

      Util.runOnMainDelayed(this, timerView.calculateAnimationDelay(timerView.startedAt, timerView.expiresIn));
    }
  }

}
