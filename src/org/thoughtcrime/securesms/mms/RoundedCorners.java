package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

public class RoundedCorners extends BitmapTransformation {
  private static final String TAG = RoundedCorners.class.getSimpleName();

  private final int radius;
  private final int colorHint;

  public RoundedCorners(@NonNull Context context, int radius, int colorHint) {
    super(context);
    this.radius = radius;
    this.colorHint = colorHint;
  }

  @Override protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth,
                                       int outHeight)
  {
    final Bitmap toReuse = pool.get(outWidth, outHeight, toTransform.getConfig() != null
                                                         ? toTransform.getConfig()
                                                         : Bitmap.Config.ARGB_8888);
    Bitmap transformed = round(toReuse, toTransform);
    if (toReuse != null && toReuse != transformed && !pool.put(toReuse)) {
      toReuse.recycle();
    }
    return transformed;
  }

  private Bitmap round(@Nullable Bitmap recycled, @Nullable Bitmap toRound) {
    Log.w(TAG, String.format("roundAndCrop(%s, %s)", recycled, toRound));
    if (toRound == null) {
      return null;
    }

    final Bitmap result;
    if (recycled != null) {
      result = recycled;
    } else {
      result = Bitmap.createBitmap(toRound.getWidth(), toRound.getHeight(), getSafeConfig(toRound));
    }

    final Canvas canvas      = new Canvas(result);
    final Paint  cornerPaint = new Paint();
    final Paint  shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    shaderPaint.setShader(new BitmapShader(toRound, TileMode.CLAMP, TileMode.CLAMP));
    cornerPaint.setColor(colorHint);
    if (!result.hasAlpha()) {
      canvas.drawRect(0, 0, radius, radius, cornerPaint);
      canvas.drawRect(0, toRound.getHeight() - radius, radius, toRound.getHeight(), cornerPaint);
      canvas.drawRect(toRound.getWidth() - radius, 0, toRound.getWidth(), radius, cornerPaint);
      canvas.drawRect(toRound.getWidth() - radius, toRound.getHeight() - radius, toRound.getWidth(), toRound.getHeight(), cornerPaint);
    }
    canvas.drawRoundRect(new RectF(0, 0, toRound.getWidth(), toRound.getHeight()), radius, radius, shaderPaint);
    return result;
  }

  private static Bitmap.Config getSafeConfig(Bitmap bitmap) {
    return bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
  }

  @Override public String getId() {
    return RoundedCorners.class.getCanonicalName();
  }
}
