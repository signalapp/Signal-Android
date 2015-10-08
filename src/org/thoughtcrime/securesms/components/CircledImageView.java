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
import android.graphics.drawable.TransitionDrawable;
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
    try {
    Bitmap roundBitmap;
    if (!(drawable instanceof GlideBitmapDrawable) && !(drawable instanceof SquaringDrawable) && !(drawable instanceof TransitionDrawable)) {
      roundBitmap = BitmapUtil.getCircleBitmap(BitmapUtil.scaleCircleCenterCrop(context, ((BitmapDrawable) drawable).getBitmap(), low));
    } else if(!(drawable instanceof SquaringDrawable) && !(drawable instanceof TransitionDrawable)){
      roundBitmap = BitmapUtil.getCircleBitmap(BitmapUtil.scaleCircleCenterCrop(context, ((GlideBitmapDrawable) drawable).getBitmap(), low));
    } else if(drawable instanceof SquaringDrawable){
      SquaringDrawable squaringDrawable = ((SquaringDrawable) drawable);
      roundBitmap = BitmapUtil.getCircleBitmap(((GlideBitmapDrawable) squaringDrawable.getCurrent()).getBitmap());
    } else {
      TransitionDrawable squaringDrawable = ((TransitionDrawable) drawable);
      roundBitmap = BitmapUtil.getCircleBitmap(((GlideBitmapDrawable) squaringDrawable.getCurrent()).getBitmap());
    }
    canvas.drawBitmap(roundBitmap, 0, padding, null);
    roundBitmap.recycle();
    } catch (OutOfMemoryError E) {
       return;
    }
  }
}
