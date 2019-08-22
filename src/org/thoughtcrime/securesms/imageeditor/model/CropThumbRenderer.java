package org.thoughtcrime.securesms.imageeditor.model;

import android.graphics.Matrix;
import android.os.Parcel;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

import java.util.UUID;

/**
 * Hit tests a circle that is {@link R.dimen#crop_area_renderer_edge_size} in radius on the screen.
 * <p>
 * Does not draw anything.
 */
class CropThumbRenderer implements Renderer, ThumbRenderer {

  private final ControlPoint controlPoint;
  private final UUID         toControl;

  private final float[]      centreOnScreen = new float[2];
  private final Matrix       matrix         = new Matrix();
  private       int          size;

  CropThumbRenderer(@NonNull ControlPoint controlPoint, @NonNull UUID toControl) {
    this.controlPoint = controlPoint;
    this.toControl    = toControl;
  }

  @Override
  public ControlPoint getControlPoint() {
    return controlPoint;
  }

  @Override
  public UUID getElementToControl() {
    return toControl;
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvasMatrix.mapPoints(centreOnScreen, Bounds.CENTRE);
    rendererContext.canvasMatrix.copyTo(matrix);
    size = rendererContext.context.getResources().getDimensionPixelSize(R.dimen.crop_area_renderer_edge_size);
  }

  @Override
  public boolean hitTest(float x, float y) {
    float[] hitPointOnScreen = new float[2];
    matrix.mapPoints(hitPointOnScreen, new float[]{ x, y });

    float dx = centreOnScreen[0] - hitPointOnScreen[0];
    float dy = centreOnScreen[1] - hitPointOnScreen[1];

    return dx * dx + dy * dy < size * size;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<CropThumbRenderer> CREATOR = new Creator<CropThumbRenderer>() {
    @Override
    public CropThumbRenderer createFromParcel(Parcel in) {
      return new CropThumbRenderer(ControlPoint.values()[in.readInt()], ParcelUtils.readUUID(in));
    }

    @Override
    public CropThumbRenderer[] newArray(int size) {
      return new CropThumbRenderer[size];
    }
  };

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(controlPoint.ordinal());
    ParcelUtils.writeUUID(dest, toControl);
  }
}
