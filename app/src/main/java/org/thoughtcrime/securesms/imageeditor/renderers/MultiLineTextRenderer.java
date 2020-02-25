package org.thoughtcrime.securesms.imageeditor.renderers;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Parcel;
import android.view.animation.Interpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.ColorableRenderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Renders multiple lines of {@link #text} in ths specified {@link #color}.
 * <p>
 * Scales down the text size of long lines to fit inside the {@link Bounds} width.
 */
public final class MultiLineTextRenderer extends InvalidateableRenderer implements ColorableRenderer {

  @NonNull
  private String text = "";

  @ColorInt
  private int color;

  private final Paint paint          = new Paint();
  private final Paint selectionPaint = new Paint();

  private final float textScale;

  private int     selStart;
  private int     selEnd;
  private boolean hasFocus;

  private List<Line> lines = emptyList();

  private ValueAnimator cursorAnimator;
  private float         cursorAnimatedValue;

  private final Matrix recommendedEditorMatrix = new Matrix();

  public MultiLineTextRenderer(@Nullable String text, @ColorInt int color) {
    setColor(color);
    float regularTextSize = paint.getTextSize();
    paint.setAntiAlias(true);
    paint.setTextSize(100);
    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    textScale = paint.getTextSize() / regularTextSize;
    selectionPaint.setAntiAlias(true);
    setText(text != null ? text : "");
    createLinesForText();
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    super.render(rendererContext);

    for (Line line : lines) {
      line.render(rendererContext);
    }
  }

  @NonNull
  public String getText() {
    return text;
  }

  public void setText(@NonNull String text) {
    if (!this.text.equals(text)) {
      this.text = text;
      createLinesForText();
    }
  }

  /**
   * Post concats an additional matrix to the supplied matrix that scales and positions the editor
   * so that all the text is visible.
   *
   * @param matrix editor matrix, already zoomed and positioned to fit the regular bounds.
   */
  public void applyRecommendedEditorMatrix(@NonNull Matrix matrix) {
    recommendedEditorMatrix.reset();

    float scale = 1f;
    for (Line line : lines) {
      if (line.scale < scale) {
        scale = line.scale;
      }
    }

    float yOff = 0;
    for (Line line : lines) {
      if (line.containsSelectionEnd()) {
        break;
      } else {
        yOff -= line.heightInBounds;
      }
    }

    recommendedEditorMatrix.postTranslate(0, Bounds.TOP / 1.5f + yOff);

    recommendedEditorMatrix.postScale(scale, scale);

    matrix.postConcat(recommendedEditorMatrix);
  }

  private void createLinesForText() {
    String[] split = text.split("\n", -1);

    if (split.length == lines.size()) {
      for (int i = 0; i < split.length; i++) {
        lines.get(i).setText(split[i]);
      }
    } else {
      lines = new ArrayList<>(split.length);
      for (String s : split) {
        lines.add(new Line(s));
      }
    }
    setSelection(selStart, selEnd);
  }

  private class Line {
    private final Matrix accentMatrix            = new Matrix();
    private final Matrix decentMatrix            = new Matrix();
    private final Matrix projectionMatrix        = new Matrix();
    private final Matrix inverseProjectionMatrix = new Matrix();
    private final RectF  selectionBounds         = new RectF();
    private final RectF  textBounds              = new RectF();

    private String text;
    private int    selStart;
    private int    selEnd;
    private float  ascentInBounds;
    private float  descentInBounds;
    private float  scale = 1f;
    private float  heightInBounds;

    Line(String text) {
      this.text = text;
      recalculate();
    }

    private void recalculate() {
      RectF maxTextBounds = new RectF();
      Rect  temp          = new Rect();

      getTextBoundsWithoutTrim(text, 0, text.length(), temp);
      textBounds.set(temp);

      maxTextBounds.set(textBounds);
      float widthLimit = 150 * textScale;

      scale = 1f / Math.max(1, maxTextBounds.right / widthLimit);

      maxTextBounds.right = widthLimit;

      if (showSelectionOrCursor()) {
        Rect startTemp = new Rect();
        int startInString = Math.min(text.length(), Math.max(0, selStart));
        int endInString = Math.min(text.length(), Math.max(0, selEnd));
        String startText = this.text.substring(0, startInString);

        getTextBoundsWithoutTrim(startText, 0, startInString, startTemp);

        if (selStart != selEnd) {
          // selection
          getTextBoundsWithoutTrim(text, startInString, endInString, temp);
        } else {
          // cursor
          paint.getTextBounds("|", 0, 1, temp);
          int width = temp.width();

          temp.left  -= width;
          temp.right -= width;
        }

        temp.left  += startTemp.right;
        temp.right += startTemp.right;
        selectionBounds.set(temp);
      }

      projectionMatrix.setRectToRect(new RectF(maxTextBounds), Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
      removeTranslate(projectionMatrix);

      float[] pts = { 0, paint.ascent(), 0, paint.descent() };
      projectionMatrix.mapPoints(pts);
      ascentInBounds  = pts[1];
      descentInBounds = pts[3];
      heightInBounds  = descentInBounds - ascentInBounds;

      projectionMatrix.preTranslate(-textBounds.centerX(), 0);
      projectionMatrix.invert(inverseProjectionMatrix);

      accentMatrix.setTranslate(0, -ascentInBounds);
      decentMatrix.setTranslate(0, descentInBounds);

      invalidate();
    }

    private void removeTranslate(Matrix matrix) {
      float[] values = new float[9];

      matrix.getValues(values);
      values[2] = 0;
      values[5] = 0;
      matrix.setValues(values);
    }

    private boolean showSelectionOrCursor() {
      return (selStart >= 0             || selEnd >= 0) &&
             (selStart <= text.length() || selEnd <= text.length());
    }

    private boolean containsSelectionEnd() {
      return (selEnd >= 0) &&
             (selEnd <= text.length());
    }

    private void getTextBoundsWithoutTrim(String text, int start, int end, Rect result) {
      Rect extra   = new Rect();
      Rect xBounds = new Rect();

      String cannotBeTrimmed = "x" + text.substring(Math.max(0, start), Math.min(text.length(), end)) + "x";

      paint.getTextBounds(cannotBeTrimmed, 0, cannotBeTrimmed.length(), extra);
      paint.getTextBounds("x", 0, 1, xBounds);
      result.set(extra);
      result.right -= 2 * xBounds.width();

      int temp = result.left;
      result.left  -= temp;
      result.right -= temp;
    }

    public boolean contains(float x, float y) {
      float[] dst = new float[2];

      inverseProjectionMatrix.mapPoints(dst, new float[]{ x, y });

      return textBounds.contains(dst[0], dst[1]);
    }

    void setText(String text) {
      if (!this.text.equals(text)) {
        this.text = text;
        recalculate();
      }
    }

    public void render(@NonNull RendererContext rendererContext) {
      // add our ascent for ourselves and the next lines
      rendererContext.canvasMatrix.concat(accentMatrix);

      rendererContext.save();

      rendererContext.canvasMatrix.concat(projectionMatrix);

      if (hasFocus && showSelectionOrCursor()) {
        if (selStart == selEnd) {
          selectionPaint.setAlpha((int) (cursorAnimatedValue * 128));
        } else {
          selectionPaint.setAlpha(128);
        }
        rendererContext.canvas.drawRect(selectionBounds, selectionPaint);
      }

      int alpha = paint.getAlpha();
      paint.setAlpha(rendererContext.getAlpha(alpha));

      rendererContext.canvas.drawText(text, 0, 0, paint);

      paint.setAlpha(alpha);

      rendererContext.restore();

      // add our descent for the next lines
      rendererContext.canvasMatrix.concat(decentMatrix);
    }

    void setSelection(int selStart, int selEnd) {
      if (selStart != this.selStart || selEnd != this.selEnd) {
        this.selStart = selStart;
        this.selEnd   = selEnd;
        recalculate();
      }
    }
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
    for (Line line : lines) {
      y += line.ascentInBounds;
      if (line.contains(x, y)) return true;
      y -= line.descentInBounds;
    }
    return false;
  }

  public void setSelection(int selStart, int selEnd) {
    this.selStart = selStart;
    this.selEnd = selEnd;
    for (Line line : lines) {
      line.setSelection(selStart, selEnd);

      int length = line.text.length() + 1; // one for new line

      selStart -= length;
      selEnd   -= length;
    }
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

  public static final Creator<MultiLineTextRenderer> CREATOR = new Creator<MultiLineTextRenderer>() {
    @Override
    public MultiLineTextRenderer createFromParcel(Parcel in) {
      return new MultiLineTextRenderer(in.readString(), in.readInt());
    }

    @Override
    public MultiLineTextRenderer[] newArray(int size) {
      return new MultiLineTextRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(text);
    dest.writeInt(color);
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
