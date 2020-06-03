package org.thoughtcrime.securesms.imageeditor.renderers;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;
import org.thoughtcrime.securesms.imageeditor.model.ParcelUtils;

/**
 * A rectangle that will be rendered on the blur mask layer. Intended for blurring faces.
 */
public class FaceBlurRenderer implements Renderer {

  private static final int CORNER_RADIUS = 0;

  private final RectF  faceRect;
  private final Matrix imageProjectionMatrix;

  private FaceBlurRenderer(@NonNull RectF faceRect, @NonNull Matrix imageProjectionMatrix) {
    this.faceRect              = faceRect;
    this.imageProjectionMatrix = imageProjectionMatrix;
  }

  public FaceBlurRenderer(@NonNull RectF faceRect, @NonNull Point imageDimensions) {
    this.faceRect              = faceRect;
    this.imageProjectionMatrix = new Matrix();

    this.imageProjectionMatrix.setRectToRect(new RectF(0, 0, imageDimensions.x, imageDimensions.y), Bounds.FULL_BOUNDS, Matrix.ScaleToFit.FILL);
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.canvas.save();
    rendererContext.canvas.concat(imageProjectionMatrix);
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
    ParcelUtils.writeMatrix(dest, imageProjectionMatrix);
    ParcelUtils.writeRect(dest, faceRect);
  }

  public static final Creator<FaceBlurRenderer> CREATOR = new Creator<FaceBlurRenderer>() {
    @Override
    public FaceBlurRenderer createFromParcel(Parcel in) {
      Matrix imageProjection = ParcelUtils.readMatrix(in);
      RectF  faceRect        = ParcelUtils.readRectF (in);

      return new FaceBlurRenderer(faceRect, imageProjection);
    }

    @Override
    public FaceBlurRenderer[] newArray(int size) {
      return new FaceBlurRenderer[size];
    }
  };
}
