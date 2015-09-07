package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.bumptech.glide.request.target.SquaringDrawable;

import org.thoughtcrime.securesms.util.BitmapUtil;

/**
 * Created by jan on 01.07.15.
 */
public class CircledImageView extends ImageView {

  Context context;

  public CircledImageView(Context ctx, AttributeSet attrs) {
    super(ctx, attrs);
    this.context = ctx;
  }

  @Override
  protected void onDraw(Canvas canvas) {

    Drawable drawable = getDrawable();

    if (drawable == null) {
      return;
    }

    if (getWidth() == 0 || getHeight() == 0) {
      return;
    }
    int w = getWidth(), low = getHeight();
    low = w < low ? w : low;
    int padding = 0;
    if(getParent() != null && getParent() instanceof  LinearLayout && low != getHeight()) {
      padding = (((LinearLayout)getParent()).getHeight() - getHeight())/2;
    }
    if(low > 200) {
      padding = 0;
    }
    Bitmap b;
    Bitmap bitmap;
    Bitmap roundBitmap;
    if (!(drawable instanceof GlideBitmapDrawable) && !(drawable instanceof SquaringDrawable)) {
      b = ((BitmapDrawable) drawable).getBitmap();
      bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
      roundBitmap = getRoundedCroppedBitmap(bitmap, low);
    } else if(!(drawable instanceof SquaringDrawable)){
      b = ((GlideBitmapDrawable) drawable).getBitmap();
      bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
      roundBitmap = BitmapUtil.scaleCircleCenterCrop(context, bitmap, low);
    } else {
      SquaringDrawable squaringDrawable = (SquaringDrawable) drawable;
      b = ((GlideBitmapDrawable) squaringDrawable.getCurrent()).getBitmap();
      bitmap = b.copy(Bitmap.Config.ARGB_8888, true);
      roundBitmap = BitmapUtil.scaleCircleCenterCrop(context, bitmap, low);
    }
    canvas.drawBitmap(roundBitmap, 0, padding, null);
    bitmap.recycle();
    roundBitmap.recycle();
  }

  public static Bitmap getRoundedCroppedBitmap(Bitmap bitmap, int radius) {
    Bitmap finalBitmap;
    boolean recycle = false;
    if (bitmap.getWidth() != radius || bitmap.getHeight() != radius) {
      finalBitmap = Bitmap.createScaledBitmap(bitmap, radius, radius, false);
      recycle = true;
    }else {
      finalBitmap = bitmap;
    }
    Bitmap output = Bitmap.createBitmap(finalBitmap.getWidth(),
        finalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);

    final Paint paint = new Paint();
    final Rect rect = new Rect(0, 0, finalBitmap.getWidth(),
        finalBitmap.getHeight());

    paint.setAntiAlias(true);
    paint.setFilterBitmap(true);
    paint.setDither(true);
    canvas.drawARGB(0, 0, 0, 0);
    paint.setColor(Color.parseColor("#BAB399"));
    canvas.drawCircle(finalBitmap.getWidth() / 2 + 0.7f,
        finalBitmap.getHeight() / 2 + 0.7f,
        finalBitmap.getWidth() / 2 + 0.1f, paint);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(finalBitmap, rect, rect, paint);
    if(recycle)
      finalBitmap.recycle();
    return output;
  }
}
