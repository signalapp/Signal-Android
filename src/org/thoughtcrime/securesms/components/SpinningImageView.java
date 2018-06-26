package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

public class SpinningImageView extends AppCompatImageView {

  private static final float DEGREES_PER_SECOND = 180;

  private long  lastDrawTime;
  private float currentRotation;

  public SpinningImageView(Context context) {
    super(context);
    init();
  }

  public SpinningImageView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public SpinningImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    lastDrawTime = System.currentTimeMillis();
    setWillNotDraw(false);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    long  currentTime = System.currentTimeMillis();
    long  elapsedTime = currentTime - lastDrawTime;
    float rotate      = ((float) elapsedTime / 1000) * DEGREES_PER_SECOND;

    currentRotation += rotate;
    canvas.rotate(currentRotation, canvas.getWidth() / 2, canvas.getHeight() / 2);
    lastDrawTime = currentTime;

    super.onDraw(canvas);
    invalidate();
  }
}
