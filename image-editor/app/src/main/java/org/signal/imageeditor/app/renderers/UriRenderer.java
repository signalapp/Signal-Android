package org.signal.imageeditor.app.renderers;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.Renderer;
import org.signal.imageeditor.core.RendererContext;

public final class UriRenderer implements Renderer, Parcelable {

  private final Uri imageUri;

  private final Paint paint = new Paint();

  private final Matrix temp1 = new Matrix();
  private final Matrix temp2 = new Matrix();

  @Nullable
  private Bitmap bitmap;

  public UriRenderer(Uri imageUri) {
    this.imageUri = imageUri;
    paint.setAntiAlias(true);
  }

  private UriRenderer(Parcel in) {
    this(Uri.parse(in.readString()));
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    if (bitmap != null && bitmap.isRecycled()) bitmap = null;

    if (bitmap == null) {
      Glide.with(rendererContext.context)
              .asBitmap()
              .load(imageUri)
              .diskCacheStrategy(DiskCacheStrategy.ALL)
              .into(new SimpleTarget<Bitmap>() {
        @Override
        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
          setBitmap(resource);
          rendererContext.rendererReady.onReady(UriRenderer.this, cropMatrix(resource), new Point(resource.getWidth(), resource.getHeight()));
        }
      });
    }

    if (bitmap != null) {
      rendererContext.save();
      rendererContext.canvasMatrix.concat(temp1);

      // FYI units are pixels at this point.
      paint.setAlpha(rendererContext.getAlpha(255));
      rendererContext.canvas.drawBitmap(bitmap, 0, 0, paint);
      rendererContext.restore();
    } else {
      rendererContext.canvas.drawRect(-0.5f, -0.5f, 0.5f, 0.5f, paint);
    }
  }

  @Override
  public boolean hitTest(float x, float y) {
    return pixelNotAlpha(x, y);
  }

  private boolean pixelNotAlpha(float x, float y) {
    if (bitmap == null) return false;

    temp1.invert(temp2);

    float[] onBmp = new float[2];
    temp2.mapPoints(onBmp, new float[]{ x, y });

    int xInt = (int) onBmp[0];
    int yInt = (int) onBmp[1];

    if (xInt >= 0 && xInt < bitmap.getWidth() && yInt >= 0 && yInt < bitmap.getHeight()) {
      return (bitmap.getPixel(xInt, yInt) & 0xff000000) != 0;
    } else {
      return false;
    }
  }

  private void setBitmap(Bitmap bitmap) {
    if (bitmap != null) {
      this.bitmap = bitmap;
      RectF from = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
      temp1.setRectToRect(from, Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
    }
  }

  private static Matrix cropMatrix(Bitmap bitmap) {
    Matrix matrix = new Matrix();
    if (bitmap.getWidth() > bitmap.getHeight()) {
      matrix.preScale(1, ((float) bitmap.getHeight()) / bitmap.getWidth());
    } else {
      matrix.preScale(((float) bitmap.getWidth()) / bitmap.getHeight(), 1);
    }
    return matrix;
  }

  public static final Creator<UriRenderer> CREATOR = new Creator<UriRenderer>() {
    @Override
    public UriRenderer createFromParcel(Parcel in) {
      return new UriRenderer(in);
    }

    @Override
    public UriRenderer[] newArray(int size) {
      return new UriRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(imageUri.toString());
  }
}
