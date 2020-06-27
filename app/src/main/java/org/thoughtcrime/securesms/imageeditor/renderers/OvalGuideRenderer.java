package org.thoughtcrime.securesms.imageeditor.renderers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Parcel;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

/**
 * Renders an oval inside of the {@link Bounds}.
 * <p>
 * Hit tests outside of the bounds.
 */
public final class OvalGuideRenderer implements Renderer {

  private final @ColorRes int ovalGuideColor;

  private final Paint paint;

  private final RectF dst = new RectF();

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.save();

    Canvas  canvas     = rendererContext.canvas;
    Context context    = rendererContext.context;
    int     stroke     = context.getResources().getDimensionPixelSize(R.dimen.oval_guide_stroke_width);
    float   halfStroke = stroke / 2f;

    this.paint.setStrokeWidth(stroke);
    paint.setColor(ContextCompat.getColor(context, ovalGuideColor));

    rendererContext.mapRect(dst, Bounds.FULL_BOUNDS);
    dst.set(dst.left + halfStroke, dst.top + halfStroke, dst.right - halfStroke, dst.bottom - halfStroke);

    rendererContext.canvasMatrix.setToIdentity();
    canvas.drawOval(dst, paint);

    rendererContext.restore();
  }

  public OvalGuideRenderer(@ColorRes int color) {
    this.ovalGuideColor = color;

    this.paint = new Paint();
    this.paint.setStyle(Paint.Style.STROKE);
    this.paint.setAntiAlias(true);
  }

  @Override
  public boolean hitTest(float x, float y) {
    return !Bounds.contains(x, y);
  }

  public static final Creator<OvalGuideRenderer> CREATOR = new Creator<OvalGuideRenderer>() {
    @Override
    public @NonNull OvalGuideRenderer createFromParcel(@NonNull Parcel in) {
      return new OvalGuideRenderer(in.readInt());
    }

    @Override
    public @NonNull OvalGuideRenderer[] newArray(int size) {
      return new OvalGuideRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeInt(ovalGuideColor);
  }
}
