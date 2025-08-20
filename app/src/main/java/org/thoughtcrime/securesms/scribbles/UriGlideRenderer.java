package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.logging.Log;
import org.signal.imageeditor.core.Bounds;
import org.signal.imageeditor.core.Renderer;
import org.signal.imageeditor.core.RendererContext;
import org.signal.imageeditor.core.SelectableRenderer;
import org.signal.imageeditor.core.model.EditorElement;
import org.signal.imageeditor.core.model.EditorModel;
import org.thoughtcrime.securesms.mms.DecryptableUri;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.concurrent.ExecutionException;

/**
 * Uses Glide to load an image and implements a {@link Renderer}.
 *
 * The image can be encrypted.
 */
public final class UriGlideRenderer implements SelectableRenderer {

  private static final String TAG = Log.tag(UriGlideRenderer.class);

  private static final int PREVIEW_DIMENSION_LIMIT = 2048;
  private static final int MAX_BLUR_DIMENSION      = 300;

  public static final float WEAK_BLUR   = 3f;
  public static final float STRONG_BLUR = 25f;

  private final Uri                     imageUri;
  private final Paint                   paint                 = new Paint();
  private final Matrix                  imageProjectionMatrix = new Matrix();
  private final Matrix                  temp                  = new Matrix();
  private final Matrix                  blurScaleMatrix       = new Matrix();
  private final boolean                 decryptable;
  private final int                     maxWidth;
  private final int                     maxHeight;
  private final float                   blurRadius;
  private final RequestListener<Bitmap> bitmapRequestListener;

  private boolean selected;

  @Nullable private Bitmap bitmap;
  @Nullable private Bitmap blurredBitmap;
  @Nullable private Paint  blurPaint;

  public UriGlideRenderer(@NonNull Uri imageUri, boolean decryptable, int maxWidth, int maxHeight) {
    this(imageUri, decryptable, maxWidth, maxHeight, STRONG_BLUR);
  }

  public UriGlideRenderer(@NonNull Uri imageUri, boolean decryptable, int maxWidth, int maxHeight, float blurRadius) {
    this(imageUri, decryptable, maxWidth, maxHeight, blurRadius, null);
  }

  public UriGlideRenderer(@NonNull Uri imageUri, boolean decryptable, int maxWidth, int maxHeight, float blurRadius, @Nullable RequestListener<Bitmap> bitmapRequestListener) {
    this.imageUri              = imageUri;
    this.decryptable           = decryptable;
    this.maxWidth              = maxWidth;
    this.maxHeight             = maxHeight;
    this.blurRadius            = blurRadius;
    this.bitmapRequestListener = bitmapRequestListener;
    paint.setAntiAlias(true);
    paint.setFilterBitmap(true);
    paint.setDither(true);
  }

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    if (getBitmap() == null) {
      if (rendererContext.isBlockingLoad()) {
        try {
          Bitmap bitmap = getGlideRequestBuilder(rendererContext.context, false).submit().get();
          setBitmap(rendererContext, bitmap);
        } catch (ExecutionException | InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        getGlideRequestBuilder(rendererContext.context, true).into(new CustomTarget<Bitmap>() {
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

      rendererContext.canvas.drawBitmap(bitmap, 0, 0, rendererContext.getMaskPaint() != null ? rendererContext.getMaskPaint() : paint);

      paint.setAlpha(alpha);

      rendererContext.restore();

      renderBlurOverlay(rendererContext);
    } else if (rendererContext.isBlockingLoad()) {
      // If failed to load, we draw a black out, in case image was sticker positioned to cover private info.
      rendererContext.canvas.drawRect(Bounds.FULL_BOUNDS, paint);
    }
  }

  private void renderBlurOverlay(RendererContext rendererContext) {
      boolean renderMask = false;

      for (EditorElement child : rendererContext.getChildren()) {
        if (child.getZOrder() == EditorModel.Z_MASK) {
          renderMask = true;
          if (blurPaint == null) {
            blurPaint = new Paint();
            blurPaint.setAntiAlias(true);
            blurPaint.setFilterBitmap(true);
            blurPaint.setDither(true);
          }
          blurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
          rendererContext.setMaskPaint(blurPaint);
          child.draw(rendererContext);
        }
      }

    if (renderMask) {
      rendererContext.save();
      rendererContext.canvasMatrix.concat(imageProjectionMatrix);

      blurPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP));
      blurPaint.setMaskFilter(null);

      if (blurredBitmap == null) {
        blurredBitmap = blur(bitmap, rendererContext.context, blurRadius);

        blurScaleMatrix.setRectToRect(new RectF(0, 0, blurredBitmap.getWidth(), blurredBitmap.getHeight()),
                                      new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                                      Matrix.ScaleToFit.FILL);
      }

      rendererContext.canvas.concat(blurScaleMatrix);
      rendererContext.canvas.drawBitmap(blurredBitmap, 0, 0, blurPaint);
      blurPaint.setXfermode(null);

      rendererContext.restore();
    }
  }

  private RequestBuilder<Bitmap> getGlideRequestBuilder(@NonNull Context context, boolean preview) {
    int width  = this.maxWidth;
    int height = this.maxHeight;

    if (preview) {
      width  = Math.min(width,  PREVIEW_DIMENSION_LIMIT);
      height = Math.min(height, PREVIEW_DIMENSION_LIMIT);
    }

    return Glide.with(context)
                   .asBitmap()
                   .override(width, height)
                   .centerInside()
                   .addListener(bitmapRequestListener)
                   .load(decryptable ? new DecryptableUri(imageUri) : imageUri);
  }

  @Override
  public boolean hitTest(float x, float y) {
    return selected ? Bounds.contains(x, y) : pixelAlphaNotZero(x, y);
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
  public @Nullable Bitmap getBitmap() {
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

  private static @NonNull Bitmap blur(Bitmap bitmap, Context context, float blurRadius) {
    Point  previewSize = scaleKeepingAspectRatio(new Point(bitmap.getWidth(), bitmap.getHeight()), PREVIEW_DIMENSION_LIMIT);
    Point  blurSize    = scaleKeepingAspectRatio(new Point(previewSize.x / 2, previewSize.y / 2 ), MAX_BLUR_DIMENSION);
    Bitmap small       = BitmapUtil.createScaledBitmap(bitmap, blurSize.x, blurSize.y);

    Log.d(TAG, "Bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", Blur: " + blurSize.x + "x" + blurSize.y);

    RenderScript        rs     = RenderScript.create(context);
    Allocation          input  = Allocation.createFromBitmap(rs, small);
    Allocation          output = Allocation.createTyped(rs, input.getType());
    ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

    script.setRadius(blurRadius);
    script.setInput(input);
    script.forEach(output);

    Bitmap blurred = Bitmap.createBitmap(small.getWidth(), small.getHeight(), small.getConfig());
    output.copyTo(blurred);
    return blurred;
  }

  private static @NonNull Point scaleKeepingAspectRatio(@NonNull Point dimens, int maxDimen) {
    int outX = dimens.x;
    int outY = dimens.y;

    if (dimens.x > maxDimen || dimens.y > maxDimen) {
      outX = maxDimen;
      outY = maxDimen;

      float widthRatio  = dimens.x  / (float) maxDimen;
      float heightRatio = dimens.y / (float) maxDimen;

      if (widthRatio > heightRatio) {
        outY = (int) (dimens.y / widthRatio);
      } else {
        outX = (int) (dimens.x / heightRatio);
      }
    }

    return new Point(outX, outY);
  }

  public static final Creator<UriGlideRenderer> CREATOR = new Creator<UriGlideRenderer>() {
    @Override
    public UriGlideRenderer createFromParcel(Parcel in) {
      return new UriGlideRenderer(Uri.parse(in.readString()),
                                  in.readInt() == 1,
                                  in.readInt(),
                                  in.readInt(),
                                  in.readFloat()
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
    dest.writeFloat(blurRadius);
  }

  @Override
  public void onSelected(boolean selected) {
    if (this.selected != selected) {
      this.selected = selected;
    }
  }

  @Override
  public void getSelectionBounds(@NonNull RectF bounds) {
    bounds.set(Bounds.FULL_BOUNDS);
  }
}
