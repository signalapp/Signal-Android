package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MaskView extends View {

  private View      target;
  private ViewGroup activityContentView;
  private Paint     maskPaint;
  private Rect      drawingRect = new Rect();
  private float     targetParentTranslationY;

  private final ViewTreeObserver.OnDrawListener onDrawListener = this::invalidate;

  public MaskView(@NonNull Context context) {
    super(context);
  }

  public MaskView(@NonNull Context context, @Nullable AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    setLayerType(LAYER_TYPE_HARDWARE, maskPaint);

    maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    activityContentView = getRootView().findViewById(android.R.id.content);
  }

  public void setTarget(@Nullable View target) {
    if (this.target != null) {
      this.target.getViewTreeObserver().removeOnDrawListener(onDrawListener);
    }

    this.target = target;

    if (this.target != null) {
      this.target.getViewTreeObserver().addOnDrawListener(onDrawListener);
    }

    invalidate();
  }

  public void setTargetParentTranslationY(float targetParentTranslationY) {
    this.targetParentTranslationY = targetParentTranslationY;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    if (target == null || !target.isAttachedToWindow()) {
      return;
    }

    target.getDrawingRect(drawingRect);
    activityContentView.offsetDescendantRectToMyCoords(target, drawingRect);

    drawingRect.bottom = Math.min(drawingRect.bottom, getBottom() - getPaddingBottom());
    drawingRect.top    += targetParentTranslationY;
    drawingRect.bottom += targetParentTranslationY;

    Bitmap mask       = Bitmap.createBitmap(target.getWidth(), drawingRect.height(), Bitmap.Config.ARGB_8888);
    Canvas maskCanvas = new Canvas(mask);

    target.draw(maskCanvas);

    canvas.drawBitmap(mask, 0, drawingRect.top, maskPaint);

    mask.recycle();
  }
}
