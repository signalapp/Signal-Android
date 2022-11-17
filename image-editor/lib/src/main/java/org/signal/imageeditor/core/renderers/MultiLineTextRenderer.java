package org.signal.imageeditor.core.renderers;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcel;
import android.view.animation.Interpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.DimensionUnit;
import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.ColorableRenderer;
import org.signal.imageeditor.core.RendererContext;
import org.signal.imageeditor.core.SelectableRenderer;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Renders multiple lines of {@link #text} in ths specified {@link #color}.
 * <p>
 * Scales down the text size of long lines to fit inside the {@link Bounds} width.
 */
public final class MultiLineTextRenderer extends InvalidateableRenderer implements ColorableRenderer, SelectableRenderer {

  private static final float HIT_PADDING                  = DimensionUnit.DP.toPixels(30);
  private static final float HIGHLIGHT_HORIZONTAL_PADDING = DimensionUnit.DP.toPixels(8);
  private static final float HIGHLIGHT_TOP_PADDING        = DimensionUnit.DP.toPixels(10);
  private static final float HIGHLIGHT_BOTTOM_PADDING     = DimensionUnit.DP.toPixels(6);
  private static final float HIGHLIGHT_CORNER_RADIUS      = DimensionUnit.DP.toPixels(4);

  @NonNull
  private String text = "";

  private static final int PADDING = 10;

  @ColorInt
  private int color;

  private final Paint paint          = new Paint();
  private final Paint selectionPaint = new Paint();
  private final Paint modePaint      = new Paint();

  private final float textScale;

  private int     selStart;
  private int     selEnd;
  private boolean hasFocus;
  private Mode    mode;

  private List<Line> lines = emptyList();

  private ValueAnimator cursorAnimator;
  private float         cursorAnimatedValue;

  private final Matrix recommendedEditorMatrix = new Matrix();

  private final RectF textBounds = new RectF();

  public MultiLineTextRenderer(@Nullable String text, @ColorInt int color, @NonNull Mode mode) {
    this.mode = mode;

    modePaint.setAntiAlias(true);
    modePaint.setTextSize(100);

    setColorInternal(color);

    float regularTextSize = paint.getTextSize();

    paint.setAntiAlias(true);
    paint.setTextSize(100);

    textScale = paint.getTextSize() / regularTextSize;

    selectionPaint.setAntiAlias(true);

    setText(text != null ? text : "");
    createLinesForText();
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    super.render(rendererContext);

    paint.setTypeface(rendererContext.typefaceProvider.getSelectedTypeface(rendererContext.context, this, rendererContext.invalidate));
    modePaint.setTypeface(rendererContext.typefaceProvider.getSelectedTypeface(rendererContext.context, this, rendererContext.invalidate));

    float height = 0;
    float width  = 0;
    for (Line line : lines) {
      line.render(rendererContext);
      height += line.heightInBounds - line.ascentInBounds + line.descentInBounds;
      width = Math.max(line.textBounds.width(), width);
    }

    textBounds.set(-width - PADDING, -PADDING, width + PADDING, height / 2f + PADDING);
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

  public void nextMode() {
    setMode(Mode.fromCode(mode.code + 1));
  }

  public @NonNull Mode getMode() {
    return mode;
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
    private final Matrix ascentMatrix            = new Matrix();
    private final Matrix descentMatrix           = new Matrix();
    private final Matrix projectionMatrix        = new Matrix();
    private final Matrix inverseProjectionMatrix = new Matrix();
    private final RectF  selectionBounds         = new RectF();
    private final RectF  textBounds              = new RectF();
    private final RectF  hitBounds               = new RectF();
    private final RectF  modeBounds              = new RectF();
    private final Path   outlinerPath            = new Path();

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
      hitBounds.set(textBounds);

      hitBounds.left   -= HIT_PADDING;
      hitBounds.right  += HIT_PADDING;
      hitBounds.top    -= HIT_PADDING;
      hitBounds.bottom += HIT_PADDING;

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

      ascentMatrix.setTranslate(0, -ascentInBounds);
      descentMatrix.setTranslate(0, descentInBounds + HIGHLIGHT_TOP_PADDING + HIGHLIGHT_BOTTOM_PADDING);

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

      return hitBounds.contains(dst[0], dst[1]);
    }

    void setText(String text) {
      if (!this.text.equals(text)) {
        this.text = text;
        recalculate();
      }
    }

    public void render(@NonNull RendererContext rendererContext) {
      // add our ascent for ourselves and the next lines
      rendererContext.canvasMatrix.concat(ascentMatrix);

      rendererContext.save();

      rendererContext.canvasMatrix.concat(projectionMatrix);

      if (mode == Mode.HIGHLIGHT) {
        if(text.isEmpty()){
          modeBounds.setEmpty();
        }else{
          modeBounds.set(textBounds.left - HIGHLIGHT_HORIZONTAL_PADDING,
                         selectionBounds.top - HIGHLIGHT_TOP_PADDING,
                         textBounds.right + HIGHLIGHT_HORIZONTAL_PADDING,
                         selectionBounds.bottom + HIGHLIGHT_BOTTOM_PADDING);
        }
        int alpha = modePaint.getAlpha();
        modePaint.setAlpha(rendererContext.getAlpha(alpha));
        rendererContext.canvas.drawRoundRect(modeBounds, HIGHLIGHT_CORNER_RADIUS, HIGHLIGHT_CORNER_RADIUS, modePaint);
        modePaint.setAlpha(alpha);
      } else if (mode == Mode.UNDERLINE) {
        modeBounds.set(textBounds.left, selectionBounds.top, textBounds.right, selectionBounds.bottom);
        modeBounds.inset(-DimensionUnit.DP.toPixels(2), -DimensionUnit.DP.toPixels(2));

        modeBounds.set(modeBounds.left,
                       Math.max(modeBounds.top, modeBounds.bottom - DimensionUnit.DP.toPixels(6)),
                       modeBounds.right,
                       modeBounds.bottom - DimensionUnit.DP.toPixels(2));

        int alpha = modePaint.getAlpha();
        modePaint.setAlpha(rendererContext.getAlpha(alpha));
        rendererContext.canvas.drawRect(modeBounds, modePaint);
        modePaint.setAlpha(alpha);
      }

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

      if (mode == Mode.OUTLINE) {
        int modeAlpha = modePaint.getAlpha();
        modePaint.setAlpha(rendererContext.getAlpha(alpha));

        if (Build.VERSION.SDK_INT >= 31) {
          outlinerPath.reset();
          modePaint.getTextPath(text, 0, text.length(), 0, 0, outlinerPath);
          outlinerPath.op(outlinerPath, Path.Op.INTERSECT);
          rendererContext.canvas.drawPath(outlinerPath, modePaint);
        } else {
          rendererContext.canvas.drawText(text, 0, 0, modePaint);
        }
        modePaint.setAlpha(modeAlpha);
      }

      rendererContext.restore();

      // add our descent for the next lines
      rendererContext.canvasMatrix.concat(descentMatrix);
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
      setColorInternal(color);
    }
  }

  @Override
  public void onSelected(boolean selected) {
  }

  @Override
  public void getSelectionBounds(@NonNull RectF bounds) {
    bounds.set(textBounds);
  }

  @Override
  public boolean hitTest(float x, float y) {
    return textBounds.contains(x, y);
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

  private void setMode(@NonNull Mode mode) {
    if (this.mode != mode) {
      this.mode = mode;
      setColorInternal(color);
    }
  }

  private void setColorInternal(@ColorInt int color) {
    this.color = color;

    if (mode == Mode.REGULAR) {
      paint.setColor(color);
      selectionPaint.setColor(color);
    } else {
      paint.setColor(Color.WHITE);
      selectionPaint.setColor(Color.WHITE);
    }

    if (mode == Mode.OUTLINE) {
      modePaint.setStrokeWidth(DimensionUnit.DP.toPixels(15) / 10f);
      modePaint.setStyle(Paint.Style.STROKE);
    } else {
      modePaint.setStyle(Paint.Style.FILL);
    }

    modePaint.setColor(color);
    invalidate();
  }

  public static final Creator<MultiLineTextRenderer> CREATOR = new Creator<MultiLineTextRenderer>() {
    @Override
    public MultiLineTextRenderer createFromParcel(Parcel in) {
      return new MultiLineTextRenderer(in.readString(), in.readInt(), Mode.fromCode(in.readInt()));
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
    dest.writeInt(mode.code);
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

  public enum Mode {
    REGULAR(0),
    HIGHLIGHT(1),
    UNDERLINE(2),
    OUTLINE(3);

    private final int code;

    Mode(int code) {
      this.code = code;
    }

    private static Mode fromCode(int code) {
      for (final Mode value : Mode.values()) {
        if (value.code == code) {
          return value;
        }
      }

      return REGULAR;
    }
  }
}
