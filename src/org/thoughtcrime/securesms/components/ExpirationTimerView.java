package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import java.util.concurrent.TimeUnit;

public class ExpirationTimerView extends HourglassView {

  private final Handler handler = new Handler();

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
      if (stopped == false) return;
      else                  stopped = false;
    }

    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        setPercentage(calculateProgress(startedAt, expiresIn));


        synchronized (ExpirationTimerView.this) {
          if (!visible) {
            stopped = true;
            return;
          }
        }

        handler.postDelayed(this, calculateAnimationDelay(startedAt, expiresIn));
      }
    }, calculateAnimationDelay(this.startedAt, this.expiresIn));
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

}
