package org.thoughtcrime.securesms.imageeditor.renderers;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * A rectangle that will be rendered on the blur mask layer. Intended for blurring faces.
 */
public class FaceBlurRenderer implements Renderer {

  private static final int CORNER_RADIUS = 0;

  private final RectF  faceRect;
  private final Point  imageDimensions;
  private final Matrix scaleMatrix;

  public FaceBlurRenderer(@NonNull RectF faceRect, @NonNull Matrix matrix) {
    this.faceRect        = faceRect;
    this.imageDimensions = new Point(0, 0);
    this.scaleMatrix = matrix;
  }

  public FaceBlurRenderer(@NonNull RectF faceRect, @NonNull Point imageDimensions) {
    this.faceRect        = faceRect;
    this.imageDimensions = imageDimensions;
    this.scaleMatrix     = new Matrix();

    scaleMatrix.setRectToRect(new RectF(0, 0, this.imageDimensions.x, this.imageDimensions.y), Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvas.save();
    rendererContext.canvas.concat(scaleMatrix);
    rendererContext.canvas.drawRoundRect(faceRect, CORNER_RADIUS, CORNER_RADIUS, rendererContext.getMaskPaint());
    rendererContext.canvas.restore();
  }

  @Override
  public boolean hitTest(float x, float y) {
    return false;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeFloat(faceRect.left);
    dest.writeFloat(faceRect.top);
    dest.writeFloat(faceRect.right);
    dest.writeFloat(faceRect.bottom);
    dest.writeInt(imageDimensions.x);
    dest.writeInt(imageDimensions.y);
  }

  public static final Creator<FaceBlurRenderer> CREATOR = new Creator<FaceBlurRenderer>() {
    @Override
    public FaceBlurRenderer createFromParcel(Parcel in) {
      float left   = in.readFloat();
      float top    = in.readFloat();
      float right  = in.readFloat();
      float bottom = in.readFloat();
      int   x      = in.readInt();
      int   y      = in.readInt();

      return new FaceBlurRenderer(new RectF(left, top, right, bottom), new Point(x, y));
    }

    @Override
    public FaceBlurRenderer[] newArray(int size) {
      return new FaceBlurRenderer[size];
    }
  };
}
