package org.signal.imageeditor.app.renderers;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.RendererContext;

import java.util.concurrent.ExecutionException;

public final class UrlRenderer extends StandardHitTestRenderer {

  private static final String TAG = "UrlRenderer";
  private final String url;
  private final Paint paint = new Paint();

  private final Matrix temp1 = new Matrix();
  private final Matrix temp2 = new Matrix();

  private Bitmap bitmap;

  public UrlRenderer(@Nullable String url) {
    this.url = url;
    paint.setAntiAlias(true);
  }

  private UrlRenderer(Parcel in) {
    this(in.readString());
  }

  public static final Creator<UrlRenderer> CREATOR = new Creator<UrlRenderer>() {
    @Override
    public UrlRenderer createFromParcel(Parcel in) {
      return new UrlRenderer(in);
    }

    @Override
    public UrlRenderer[] newArray(int size) {
      return new UrlRenderer[size];
    }
  };

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    if (bitmap != null && bitmap.isRecycled()) bitmap = null;

    if (bitmap == null) {
      if (rendererContext.isBlockingLoad()) {
        try {
          setBitmap(rendererContext, Glide.with(rendererContext.context)
                                             .asBitmap()
                                             .load(url)
                                             .diskCacheStrategy(DiskCacheStrategy.ALL)
                                             .submit()
                                             .get());
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        Glide.with(rendererContext.context)
        .asBitmap()
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .into(new SimpleTarget<Bitmap>() {
          @Override
          public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            setBitmap(rendererContext, resource);
          }
        });
      }
    }

    if (bitmap != null) {
      rendererContext.save();
      rendererContext.getCurrent(temp2);
      temp2.preConcat(temp1);
      rendererContext.canvas.concat(temp1);

      // FYI units are pixels at this point.
      paint.setAlpha(rendererContext.getAlpha(255));
      rendererContext.canvas.drawBitmap(bitmap, 0, 0, paint);
      rendererContext.restore();
    } else {
      if (rendererContext.isBlockingLoad()) {
        Log.e(TAG, "blocking but drawing null :(");
      }
      rendererContext.canvas.drawRect(Bounds.FULL_BOUNDS, paint);
    }

    drawDebugInfo(rendererContext);
  }

  private void drawDebugInfo(RendererContext rendererContext) {
//    float width = bitmap.getWidth();
//    float height = bitmap.getWidth();

    //RectF bounds = new RectF(Bounds.LEFT, Bounds.TOP/2f, Bounds.RIGHT,Bounds.BOTTOM/2f );//Bounds.FULL_BOUNDS;
    RectF bounds = Bounds.FULL_BOUNDS;

    Paint paint = new Paint();
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(0xffffff00);
    rendererContext.canvas.drawRect(bounds, paint);

    RectF fullBounds = new RectF();
    rendererContext.mapRect(fullBounds, bounds);

    rendererContext.save();

    RectF dst = new RectF();
    rendererContext.mapRect(dst, bounds);
    paint.setColor(0xffff00ff);
    rendererContext.canvasMatrix.setToIdentity();
    rendererContext.canvas.drawRect(dst, paint);

    rendererContext.restore();

    rendererContext.save();

    Matrix unrotated = new Matrix();
    rendererContext.getCurrent(unrotated);
    findUnrotateMatrix(unrotated);

    Matrix rotated = new Matrix();
    rendererContext.getCurrent(rotated);
    findRotateMatrix(rotated);

    RectF dst2 = new RectF();
    unrotated.mapRect(dst2, Bounds.FULL_BOUNDS); // works because square, do we need rotated here?

    float scaleX = Bounds.FULL_BOUNDS.width() / dst2.width();
    float scaleY = Bounds.FULL_BOUNDS.height() / dst2.height();

    rendererContext.canvasMatrix.concat(unrotated);
    Matrix matrix = new Matrix();
    matrix.setScale(scaleX, scaleY);
    rendererContext.canvasMatrix.concat(matrix);

    paint.setColor(0xff0000ff);
    rendererContext.canvas.drawRect(bounds, paint);

    rendererContext.restore();
  }

/**
   * Given a scaled/rotated and transformed matrix, extract just the rotate and reverse it.
   */
  private void findUnrotateMatrix(@NonNull Matrix matrix) {
    float[] values = new float[9];

    matrix.getValues(values);

    float xScale = (float) Math.sqrt(values[0] * values[0] + values[3] * values[3]);
    float yScale = (float) Math.sqrt(values[1] * values[1] + values[4] * values[4]);

    values[0] /= xScale;
    values[1] /= -yScale;
    values[2] = 0;

    values[3] /= -xScale;
    values[4] /= yScale;
    values[5] = 0;

    matrix.setValues(values);
  }

    /**
   * Given a scaled/rotated and transformed matrix, extract just the rotate and reverse it.
   */
  private void findRotateMatrix(@NonNull Matrix matrix) {
    float[] values = new float[9];

    matrix.getValues(values);

    float xScale = (float) Math.sqrt(values[0] * values[0] + values[3] * values[3]);
    float yScale = (float) Math.sqrt(values[1] * values[1] + values[4] * values[4]);

    values[0] /= xScale;
    values[1] /= yScale;
    values[2] = 0;

    values[3] /= xScale;
    values[4] /= yScale;
    values[5] = 0;

    matrix.setValues(values);
  }

  @Override
  public boolean hitTest(float x, float y) {
    return super.hitTest(x, y) && pixelNotAlpha(x, y);
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
      return xInt >= 0 && xInt <= bitmap.getWidth() && yInt >= 0 && yInt <= bitmap.getHeight();
    }
  }

  private void setBitmap(@NonNull RendererContext rendererContext, @Nullable Bitmap bitmap) {
    this.bitmap = bitmap;
    if (bitmap != null) {
      RectF from = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
      temp1.setRectToRect(from, Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
      rendererContext.rendererReady.onReady(this, cropMatrix(bitmap), new Point(bitmap.getWidth(), bitmap.getHeight()));
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

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(url);
  }
}
