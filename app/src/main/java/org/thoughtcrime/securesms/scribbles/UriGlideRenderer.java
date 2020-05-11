package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;

import java.util.concurrent.ExecutionException;

/**
 * Uses Glide to load an image and implements a {@link Renderer}.
 *
 * The image can be encrypted.
 */
final class UriGlideRenderer implements Renderer {

  private static final int PREVIEW_DIMENSION_LIMIT = 2048;

  private final Uri     imageUri;
  private final Paint   paint                 = new Paint();
  private final Matrix  imageProjectionMatrix = new Matrix();
  private final Matrix  temp                  = new Matrix();
  private final boolean decryptable;
  private final int     maxWidth;
  private final int     maxHeight;

  @Nullable
  private Bitmap bitmap;

  UriGlideRenderer(@NonNull Uri imageUri, boolean decryptable, int maxWidth, int maxHeight) {
    this.imageUri    = imageUri;
    this.decryptable = decryptable;
    this.maxWidth    = maxWidth;
    this.maxHeight   = maxHeight;
    paint.setAntiAlias(true);
    paint.setFilterBitmap(true);
    paint.setDither(true);
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    if (getBitmap() == null) {
      if (rendererContext.isBlockingLoad()) {
        try {
          Bitmap bitmap = getBitmapGlideRequest(rendererContext.context, false).submit().get();
          setBitmap(rendererContext, bitmap);
        } catch (ExecutionException | InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        getBitmapGlideRequest(rendererContext.context, true).into(new CustomTarget<Bitmap>() {
          @Override
          public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            setBitmap(rendererContext, resource);

            rendererContext.invalidate.onInvalidate(UriGlideRenderer.this);
          }

          @Override
          public void onLoadCleared(@Nullable Drawable placeholder) {
            bitmap = null;
          }
        });
      }
    }

    final Bitmap bitmap = getBitmap();
    if (bitmap != null) {
      rendererContext.save();

      rendererContext.canvasMatrix.concat(imageProjectionMatrix);

      // Units are image level pixels at this point.

      int alpha = paint.getAlpha();
      paint.setAlpha(rendererContext.getAlpha(alpha));

      rendererContext.canvas.drawBitmap(bitmap, 0, 0, paint);

      paint.setAlpha(alpha);

      rendererContext.restore();
    } else if (rendererContext.isBlockingLoad()) {
      // If failed to load, we draw a black out, in case image was sticker positioned to cover private info.
      rendererContext.canvas.drawRect(Bounds.FULL_BOUNDS, paint);
    }
  }

  private GlideRequest<Bitmap> getBitmapGlideRequest(@NonNull Context context, boolean preview) {
    int width  = this.maxWidth;
    int height = this.maxHeight;

    if (preview) {
      width  = Math.min(width,  PREVIEW_DIMENSION_LIMIT);
      height = Math.min(height, PREVIEW_DIMENSION_LIMIT);
    }

    return GlideApp.with(context)
                   .asBitmap()
                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                   .override(width, height)
                   .centerInside()
                   .load(decryptable ? new DecryptableStreamUriLoader.DecryptableUri(imageUri) : imageUri);
  }

  @Override
  public boolean hitTest(float x, float y) {
    return pixelAlphaNotZero(x, y);
  }

  private boolean pixelAlphaNotZero(float x, float y) {
    Bitmap bitmap = getBitmap();

    if (bitmap == null) return false;

    imageProjectionMatrix.invert(temp);

    float[] onBmp = new float[2];
    temp.mapPoints(onBmp, new float[]{ x, y });

    int xInt = (int) onBmp[0];
    int yInt = (int) onBmp[1];

    if (xInt >= 0 && xInt < bitmap.getWidth() && yInt >= 0 && yInt < bitmap.getHeight()) {
      return (bitmap.getPixel(xInt, yInt) & 0xff000000) != 0;
    } else {
      return false;
    }
  }

  /**
   * Always use this getter, as Bitmap is kept in Glide's LRUCache, so it could have been recycled
   * by Glide. If it has, or was never set, this method returns null.
   */
  private @Nullable Bitmap getBitmap() {
    if (bitmap != null && bitmap.isRecycled()) {
      bitmap = null;
    }
    return bitmap;
  }

  private void setBitmap(@NonNull RendererContext rendererContext, @Nullable Bitmap bitmap) {
    this.bitmap = bitmap;
    if (bitmap != null) {
      RectF from  = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
      imageProjectionMatrix.setRectToRect(from, Bounds.FULL_BOUNDS, Matrix.ScaleToFit.CENTER);
      rendererContext.rendererReady.onReady(UriGlideRenderer.this, cropMatrix(bitmap), new Point(bitmap.getWidth(), bitmap.getHeight()));
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

  public static final Creator<UriGlideRenderer> CREATOR = new Creator<UriGlideRenderer>() {
    @Override
    public UriGlideRenderer createFromParcel(Parcel in) {
      return new UriGlideRenderer(Uri.parse(in.readString()),
                                  in.readInt() == 1,
                                  in.readInt(),
                                  in.readInt()
                                 );
    }

    @Override
    public UriGlideRenderer[] newArray(int size) {
      return new UriGlideRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(imageUri.toString());
    dest.writeInt(decryptable ? 1 : 0);
    dest.writeInt(maxWidth);
    dest.writeInt(maxHeight);
  }
}
