package org.thoughtcrime.securesms.imageeditor.renderers;

import android.graphics.Path;
import android.os.Parcel;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

/**
 * Renders the {@link color} outside of the {@link Bounds}.
 * <p>
 * Hit tests outside of the bounds.
 */
public final class InverseFillRenderer implements Renderer {

  private final int color;

  private final Path path = new Path();

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvas.save();
    rendererContext.canvas.clipPath(path);
    rendererContext.canvas.drawColor(color);
    rendererContext.canvas.restore();
  }

  public InverseFillRenderer(@ColorInt int color) {
    this.color = color;
    path.toggleInverseFillType();
    path.moveTo(Bounds.LEFT, Bounds.TOP);
    path.lineTo(Bounds.RIGHT, Bounds.TOP);
    path.lineTo(Bounds.RIGHT, Bounds.BOTTOM);
    path.lineTo(Bounds.LEFT, Bounds.BOTTOM);
    path.close();
  }

  private InverseFillRenderer(Parcel in) {
    this(in.readInt());
  }

  @Override
  public boolean hitTest(float x, float y) {
    return !Bounds.contains(x, y);
  }

  public static final Creator<InverseFillRenderer> CREATOR = new Creator<InverseFillRenderer>() {
    @Override
    public InverseFillRenderer createFromParcel(Parcel in) {
      return new InverseFillRenderer(in);
    }

    @Override
    public InverseFillRenderer[] newArray(int size) {
      return new InverseFillRenderer[size];
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
