package org.thoughtcrime.securesms.imageeditor.renderers;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.animation.Interpolator;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.ColorableRenderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

/**
 * Renders a single line of {@link #text} in ths specified {@link #color}.
 * <p>
 * Scales down the text size to fit inside the {@link Bounds} width.
 */
public final class TextRenderer extends InvalidateableRenderer implements ColorableRenderer {

  @NonNull
  private String text = "";

  @ColorInt
  private int color;

  private final Paint  paint                   = new Paint();
  private final Paint  selectionPaint          = new Paint();
  private final RectF  textBounds              = new RectF();
  private final RectF  selectionBounds         = new RectF();
  private final RectF  maxTextBounds           = new RectF();
  private final Matrix projectionMatrix        = new Matrix();
  private final Matrix inverseProjectionMatrix = new Matrix();

  private final float textScale;

  private float   xForCentre;
  private int     selStart;
  private int     selEnd;
  private boolean hasFocus;

  private ValueAnimator cursorAnimator;
  private float         cursorAnimatedValue;

  public TextRenderer(@Nullable String text, @ColorInt int color) {
    setColor(color);
    float regularTextSize = paint.getTextSize();
    paint.setAntiAlias(true);
    paint.setTextSize(100);
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    textScale = paint.getTextSize() / regularTextSize;
    selectionPaint.setAntiAlias(true);
    setText(text != null ? text : "");
  }

  private TextRenderer(Parcel in) {
    this(in.readString(), in.readInt());
  }

  public static final Creator<TextRenderer> CREATOR = new Creator<TextRenderer>() {
    @Override
    public TextRenderer createFromParcel(Parcel in) {
      return new TextRenderer(in);
    }

    @Override
    public TextRenderer[] newArray(int size) {
      return new TextRenderer[size];
    }
  };

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    super.render(rendererContext);
    rendererContext.save();
    Canvas canvas = rendererContext.canvas;

    rendererContext.canvasMatrix.concat(projectionMatrix);

    if (hasFocus) {
      if (selStart == selEnd) {
        selectionPaint.setAlpha((int) (cursorAnimatedValue * 128));
      } else {
        selectionPaint.setAlpha(128);
      }
      canvas.drawRect(selectionBounds, selectionPaint);
    }

    int alpha = paint.getAlpha();
    paint.setAlpha(rendererContext.getAlpha(alpha));

    canvas.drawText(text, xForCentre, 0, paint);

    paint.setAlpha(alpha);

    rendererContext.restore();
  }

  @NonNull
  public String getText() {
    return text;
  }

  public void setText(@NonNull String text) {
    if (!this.text.equals(text)) {
      this.text = text;
      recalculate();
    }
  }

  private void recalculate() {
    Rect temp = new Rect();

    getTextBoundsWithoutTrim(text, 0, text.length(), temp);
    textBounds.set(temp);

    maxTextBounds.set(textBounds);
    maxTextBounds.right = Math.max(150 * textScale, maxTextBounds.right);

    xForCentre = maxTextBounds.centerX() - textBounds.centerX();

    textBounds.left  += xForCentre;
    textBounds.right += xForCentre;

    if (selStart != selEnd) {
      getTextBoundsWithoutTrim(text, Math.min(text.length(), selStart), Math.min(text.length(), selEnd), temp);
    } else {
      Rect startTemp = new Rect();
      int start      = Math.min(text.length(), selStart);
      String text    = this.text.substring(0, start);

      getTextBoundsWithoutTrim(text, 0, start, startTemp);
      paint.getTextBounds("|", 0, 1, temp);

      int width   = temp.width();

      temp.left  -= width;
      temp.right -= width;
      temp.left  += startTemp.right;
      temp.right += startTemp.right;
    }
    selectionBounds.set(temp);
    selectionBounds.left  += xForCentre;
    selectionBounds.right += xForCentre;

    projectionMatrix.setRectToRect(new RectF(maxTextBounds), Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
    projectionMatrix.invert(inverseProjectionMatrix);
    invalidate();
  }

  private void getTextBoundsWithoutTrim(String text, int start, int end, Rect result) {
    Rect extra = new Rect();
    Rect xBounds = new Rect();
    String cannotBeTrimmed = "x" + text.substring(start, end) + "x";
    paint.getTextBounds(cannotBeTrimmed, 0, cannotBeTrimmed.length(), extra);
    paint.getTextBounds("x", 0, 1, xBounds);
    result.set(extra);
    result.right -= 2 * xBounds.width();
  }

  @Override
  public int getColor() {
    return color;
  }

  @Override
  public void setColor(@ColorInt int color) {
    if (this.color != color) {
      this.color = color;
      paint.setColor(color);
      selectionPaint.setColor(color);
      invalidate();
    }
  }

  @Override
  public boolean hitTest(float x, float y) {
    float[] dst = new float[2];
    inverseProjectionMatrix.mapPoints(dst, new float[]{ x, y });
    return textBounds.contains(dst[0], dst[1]);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(text);
    dest.writeInt(color);
  }

  public void setSelection(int selStart, int selEnd) {
    this.selStart = selStart;
    this.selEnd = selEnd;
    recalculate();
  }

  public void setFocused(boolean hasFocus) {
    if (this.hasFocus != hasFocus) {
      this.hasFocus = hasFocus;
      if (cursorAnimator != null) {
          cursorAnimator.cancel();
          cursorAnimator = null;
      }
      if (hasFocus) {
        cursorAnimator = ValueAnimator.ofFloat(0, 1);
        cursorAnimator.setInterpolator(pulseInterpolator());
        cursorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        cursorAnimator.setDuration(1000);
        cursorAnimator.addUpdateListener(animation -> {
          cursorAnimatedValue = (float) animation.getAnimatedValue();
          invalidate();
        });
        cursorAnimator.start();
      } else {
        invalidate();
      }
    }
  }

  private static Interpolator pulseInterpolator() {
    return input -> {
      input *= 5;
      if (input > 1) {
        input = 4 - input;
      }
      return Math.max(0, Math.min(1, input));
    };
  }
}
