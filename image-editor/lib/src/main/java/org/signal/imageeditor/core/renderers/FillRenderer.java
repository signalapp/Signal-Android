package org.signal.imageeditor.core.renderers;

import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.signal.core.util.DimensionUnit;
import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.Renderer;
import org.signal.imageeditor.core.RendererContext;

/**
 * Renders the {@link color} outside of the {@link Bounds}.
 * <p>
 * Hit tests outside of the bounds.
 */
public final class FillRenderer implements Renderer {

  private final int color;

  private final RectF dst  = new RectF();
  private final Path  path = new Path();

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvas.save();

    rendererContext.mapRect(dst, Bounds.FULL_BOUNDS);
    rendererContext.canvasMatrix.setToIdentity();

    path.reset();
    path.addRoundRect(dst, DimensionUnit.DP.toPixels(18), DimensionUnit.DP.toPixels(18), Path.Direction.CW);

    rendererContext.canvas.clipPath(path);
    rendererContext.canvas.drawColor(color);
    rendererContext.canvas.restore();
  }

  public FillRenderer(@ColorInt int color) {
    this.color = color;
  }

  private FillRenderer(Parcel in) {
    this(in.readInt());
  }

  @Override
  public boolean hitTest(float x, float y) {
    return !Bounds.contains(x, y);
  }

  public static final Creator<FillRenderer> CREATOR = new Creator<FillRenderer>() {
    @Override
    public FillRenderer createFromParcel(Parcel in) {
      return new FillRenderer(in);
    }

    @Override
    public FillRenderer[] newArray(int size) {
      return new FillRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(color);
  }
}
