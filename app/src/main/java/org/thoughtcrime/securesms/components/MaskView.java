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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MaskView extends View {

  private MaskTarget maskTarget;
  private ViewGroup  activityContentView;
  private Paint      maskPaint;
  private Rect       drawingRect = new Rect();
  private float      targetParentTranslationY;

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

  public void setTarget(@Nullable MaskTarget maskTarget) {
    if (this.maskTarget != null) {
      removeOnDrawListener(this.maskTarget, onDrawListener);
    }

    this.maskTarget = maskTarget;

    if (this.maskTarget != null) {
      addOnDrawListener(maskTarget, onDrawListener);
    }

    invalidate();
  }

  public void setTargetParentTranslationY(float targetParentTranslationY) {
    this.targetParentTranslationY = targetParentTranslationY;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    if (nothingToMask(maskTarget)) {
      return;
    }

    maskTarget.getPrimaryTarget().getDrawingRect(drawingRect);
    activityContentView.offsetDescendantRectToMyCoords(maskTarget.getPrimaryTarget(), drawingRect);

    drawingRect.top    += targetParentTranslationY;
    drawingRect.bottom += targetParentTranslationY;

    Bitmap mask       = Bitmap.createBitmap(maskTarget.getPrimaryTarget().getWidth(), drawingRect.height(), Bitmap.Config.ARGB_8888);
    Canvas maskCanvas = new Canvas(mask);

    maskTarget.draw(maskCanvas);

    canvas.clipRect(drawingRect.left, Math.max(drawingRect.top, getTop() + getPaddingTop()), drawingRect.right, Math.min(drawingRect.bottom, getBottom() - getPaddingBottom()));

    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) maskTarget.getPrimaryTarget().getLayoutParams();
    canvas.drawBitmap(mask, params.leftMargin, drawingRect.top, maskPaint);

    mask.recycle();
  }

  private static void removeOnDrawListener(@NonNull MaskTarget maskTarget, @NonNull ViewTreeObserver.OnDrawListener onDrawListener) {
    for (View view : maskTarget.getAllTargets()) {
      if (view != null) {
        view.getViewTreeObserver().removeOnDrawListener(onDrawListener);
      }
    }
  }

  private static void addOnDrawListener(@NonNull MaskTarget maskTarget, @NonNull ViewTreeObserver.OnDrawListener onDrawListener) {
    for (View view : maskTarget.getAllTargets()) {
      if (view != null) {
        view.getViewTreeObserver().addOnDrawListener(onDrawListener);
      }
    }
  }

  private static boolean nothingToMask(@Nullable MaskTarget maskTarget) {
    if (maskTarget == null) {
      return true;
    }

    for (View view : maskTarget.getAllTargets()) {
      if (view == null || !view.isAttachedToWindow()) {
        return true;
      }
    }

    return false;
  }

  public static class MaskTarget {

    private final View primaryTarget;

    public MaskTarget(@NonNull View primaryTarget) {
      this.primaryTarget = primaryTarget;
    }

    final @NonNull View getPrimaryTarget() {
      return primaryTarget;
    }

    protected  @NonNull List<View> getAllTargets() {
      return Collections.singletonList(primaryTarget);
    }

    protected void draw(@NonNull Canvas canvas) {
      primaryTarget.draw(canvas);
    }
  }
}
