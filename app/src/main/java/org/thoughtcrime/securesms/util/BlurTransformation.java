package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import org.whispersystems.signalservice.api.util.Preconditions;

import java.security.MessageDigest;
import java.util.Locale;

public final class BlurTransformation extends BitmapTransformation {

  public static final float MAX_RADIUS = 25f;

  private final RenderScript rs;
  private final float        bitmapScaleFactor;
  private final float        blurRadius;

  public BlurTransformation(@NonNull Context context, float bitmapScaleFactor, float blurRadius) {
    rs = RenderScript.create(context);

    Preconditions.checkArgument(blurRadius >= 0 && blurRadius <= 25, "Blur radius must be a non-negative value less than or equal to 25.");
    Preconditions.checkArgument(bitmapScaleFactor > 0, "Bitmap scale factor must be a non-negative value");

    this.bitmapScaleFactor = bitmapScaleFactor;
    this.blurRadius        = blurRadius;
  }

  @Override
  protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
    Matrix scaleMatrix = new Matrix();
    scaleMatrix.setScale(bitmapScaleFactor, bitmapScaleFactor);

    int                 targetWidth   = Math.min(outWidth, toTransform.getWidth());
    int                 targetHeight  = Math.min(outHeight, toTransform.getHeight());
    Bitmap              blurredBitmap = Bitmap.createBitmap(toTransform, 0, 0, targetWidth, targetHeight, scaleMatrix, true);
    Allocation          input         = Allocation.createFromBitmap(rs, blurredBitmap, Allocation.MipmapControl.MIPMAP_FULL, Allocation.USAGE_SHARED);
    Allocation          output        = Allocation.createTyped(rs, input.getType());
    ScriptIntrinsicBlur script        = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

    script.setInput(input);
    script.setRadius(blurRadius);
    script.forEach(output);
    output.copyTo(blurredBitmap);

    return blurredBitmap;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(String.format(Locale.US, "blur-%f-%f", bitmapScaleFactor, blurRadius).getBytes());
  }
}
