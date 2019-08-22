package org.thoughtcrime.securesms.mediasend;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

public class FlipTransformation extends BitmapTransformation {

  @Override
  protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
    Bitmap output = pool.get(toTransform.getWidth(), toTransform.getHeight(), toTransform.getConfig());

    Canvas canvas = new Canvas(output);
    Matrix matrix = new Matrix();
    matrix.setScale(-1, 1);
    matrix.postTranslate(toTransform.getWidth(), 0);

    canvas.drawBitmap(toTransform, matrix, null);

    return output;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(FlipTransformation.class.getSimpleName().getBytes());
  }
}
