package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;

import org.thoughtcrime.securesms.util.ResUtil;

public class RoundedCorners extends BitmapTransformation {
  private final boolean crop;
  private final int     radius;
  private final int     colorHint;

  public RoundedCorners(@NonNull Context context, boolean crop, int radius, int colorHint) {
    super(context);
    this.crop      = crop;
    this.radius    = radius;
    this.colorHint = colorHint;
  }

  public RoundedCorners(@NonNull Context context, int radius) {
    this(context, true, radius, ResUtil.getColor(context, android.R.attr.windowBackground));
  }

  @Override protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth,
                                       int outHeight)
  {
    final Bitmap toRound = crop ? centerCrop(pool, toTransform, outWidth, outHeight)
                                : fitCenter(pool, toTransform, outWidth, outHeight);

    final Bitmap rounded = round(pool, toRound);

    if (toRound != null && toRound != rounded && toRound != toTransform && !pool.put(toRound)) {
      toRound.recycle();
    }

    return rounded;
  }

  private Bitmap centerCrop(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
    final Bitmap toReuse     = pool.get(outWidth, outHeight, getSafeConfig(toTransform));
    final Bitmap transformed = TransformationUtils.centerCrop(toReuse, toTransform, outWidth, outHeight);

    if (toReuse != null && toReuse != transformed && !pool.put(toReuse)) {
      toReuse.recycle();
    }

    return transformed;
  }

  private Bitmap fitCenter(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
    return TransformationUtils.fitCenter(toTransform, pool, outWidth, outHeight);
  }

  private Bitmap round(@NonNull BitmapPool pool, @Nullable Bitmap toRound) {
    if (toRound == null) {
      return null;
    }

    Bitmap result = pool.get(toRound.getWidth(), toRound.getHeight(), getSafeConfig(toRound));

    if (result == null) {
      result = Bitmap.createBitmap(toRound.getWidth(), toRound.getHeight(), getSafeConfig(toRound));
    }

    Canvas canvas = new Canvas(result);

    if (Config.RGB_565.equals(result.getConfig())) {
      Paint  cornerPaint = new Paint();
      cornerPaint.setColor(colorHint);

      canvas.drawRect(0, 0, radius, radius, cornerPaint);
      canvas.drawRect(0, toRound.getHeight() - radius, radius, toRound.getHeight(), cornerPaint);
      canvas.drawRect(toRound.getWidth() - radius, 0, toRound.getWidth(), radius, cornerPaint);
      canvas.drawRect(toRound.getWidth() - radius, toRound.getHeight() - radius, toRound.getWidth(), toRound.getHeight(), cornerPaint);
    }

    Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    shaderPaint.setShader(new BitmapShader(toRound, TileMode.CLAMP, TileMode.CLAMP));

    canvas.drawRoundRect(new RectF(0, 0, toRound.getWidth(), toRound.getHeight()), radius, radius, shaderPaint);

    return result;
  }

  private static Bitmap.Config getSafeConfig(Bitmap bitmap) {
    return bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
  }

  @Override
  public String getId() {
    return RoundedCorners.class.getCanonicalName();
  }
}
