package org.thoughtcrime.securesms.components.emoji;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiProvider {
  private static final    String        TAG      = EmojiProvider.class.getSimpleName();
  private static volatile EmojiProvider instance = null;
  private static final    Paint         paint    = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

  private final SparseArray<DrawInfo> offsets = new SparseArray<>();

  @SuppressWarnings("MalformedRegex")
  //                                                            0x20a0-0x32ff          0x1f00-0x1fff              0xfe4e5-0xfe4ee
  //                                                           |==== misc ====||======== emoticons ========||========= flags ==========|
  private static final Pattern EMOJI_RANGE = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]");

  public static final int    EMOJI_RAW_HEIGHT = 64;
  public static final int    EMOJI_RAW_WIDTH  = 64;
  public static final int    EMOJI_VERT_PAD   = 0;
  public static final int    EMOJI_PER_ROW    = 32;

  private final Context context;
  private final float   decodeScale;
  private final float   verticalPad;

  public static EmojiProvider getInstance(Context context) {
    if (instance == null) {
      synchronized (EmojiProvider.class) {
        if (instance == null) {
          instance = new EmojiProvider(context);
        }
      }
    }
    return instance;
  }

  private EmojiProvider(Context context) {
    this.context     = context.getApplicationContext();
    this.decodeScale = Math.min(1f, context.getResources().getDimension(R.dimen.emoji_drawer_size) / EMOJI_RAW_HEIGHT);
    this.verticalPad = EMOJI_VERT_PAD * this.decodeScale;
    for (EmojiPageModel page : EmojiPages.PAGES) {
      if (page.hasSpriteMap()) {
        final EmojiPageBitmap pageBitmap = new EmojiPageBitmap(page);
        for (int i=0; i < page.getEmoji().length; i++) {
          offsets.put(Character.codePointAt(page.getEmoji()[i], 0), new DrawInfo(pageBitmap, i));
        }
      }
    }
  }

  public Spannable emojify(CharSequence text, TextView tv) {
    Matcher                matches = EMOJI_RANGE.matcher(text);
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    while (matches.find()) {
      int codePoint = matches.group().codePointAt(0);
      Drawable drawable = getEmojiDrawable(codePoint);
      if (drawable != null) {
        builder.setSpan(new EmojiSpan(drawable, tv), matches.start(), matches.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  public Drawable getEmojiDrawable(int emojiCode) {
    return getEmojiDrawable(offsets.get(emojiCode));
  }

  private Drawable getEmojiDrawable(DrawInfo drawInfo) {
    if (drawInfo == null)  {
      return null;
    }

    final EmojiDrawable drawable = new EmojiDrawable(drawInfo, decodeScale);
    drawInfo.page.get().addListener(new FutureTaskListener<Bitmap>() {
      @Override public void onSuccess(final Bitmap result) {
        Util.runOnMain(new Runnable() {
          @Override public void run() {
            drawable.setBitmap(result);
          }
        });
      }

      @Override public void onFailure(Throwable error) {
        Log.w(TAG, error);
      }
    });
    return drawable;
  }

  public class EmojiDrawable extends Drawable {
    private final DrawInfo info;
    private       Bitmap   bmp;
    private       float    intrinsicWidth;
    private       float    intrinsicHeight;

    @Override public int getIntrinsicWidth() {
      return (int)intrinsicWidth;
    }

    @Override public int getIntrinsicHeight() {
      return (int)intrinsicHeight;
    }

    public EmojiDrawable(DrawInfo info, float decodeScale) {
      this.info            = info;
      this.intrinsicWidth  = EMOJI_RAW_WIDTH  * decodeScale;
      this.intrinsicHeight = EMOJI_RAW_HEIGHT * decodeScale;
    }

    @Override
    public void draw(Canvas canvas) {
      if (bmp == null) {
        return;
      }

      final int row = info.index / EMOJI_PER_ROW;
      final int row_index = info.index % EMOJI_PER_ROW;

      canvas.drawBitmap(bmp,
                        new Rect((int)(row_index * intrinsicWidth),
                                 (int)(row * intrinsicHeight + row * verticalPad),
                                 (int)((row_index + 1) * intrinsicWidth),
                                 (int)((row + 1) * intrinsicHeight + row * verticalPad)),
                        getBounds(),
                        paint);
    }

    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    public void setBitmap(Bitmap bitmap) {
      Util.assertMainThread();
      if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB_MR1 || bmp == null || !bmp.sameAs(bitmap)) {
        bmp = bitmap;
        invalidateSelf();
      }
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter cf) { }
  }

  class DrawInfo {
    EmojiPageBitmap page;
    int             index;

    public DrawInfo(final EmojiPageBitmap page, final int index) {
      this.page = page;
      this.index = index;
    }

    @Override
    public String toString() {
      return "DrawInfo{" +
          "page=" + page +
          ", index=" + index +
          '}';
    }
  }

  private class EmojiPageBitmap {
    private EmojiPageModel               model;
    private SoftReference<Bitmap>        bitmapReference;
    private ListenableFutureTask<Bitmap> task;

    public EmojiPageBitmap(EmojiPageModel model) {
      this.model = model;
    }

    private ListenableFutureTask<Bitmap> get() {
      Util.assertMainThread();

      if (bitmapReference != null && bitmapReference.get() != null) {
        return new ListenableFutureTask<>(bitmapReference.get());
      } else if (task != null) {
        return task;
      } else {
        Callable<Bitmap> callable = new Callable<Bitmap>() {
          @Override public Bitmap call() throws Exception {
            try {
              Log.w(TAG, "loading page " + model.getSprite());
              return loadPage();
            } catch (IOException | ExecutionException ioe) {
              Log.w(TAG, ioe);
            }
            return null;
          }
        };
        task = new ListenableFutureTask<>(callable);
        new AsyncTask<Void, Void, Void>() {
          @Override protected Void doInBackground(Void... params) {
            task.run();
            return null;
          }

          @Override protected void onPostExecute(Void aVoid) {
            task = null;
          }
        }.execute();
      }
      return task;
    }

    private Bitmap loadPage() throws IOException, ExecutionException {
      if (bitmapReference != null && bitmapReference.get() != null) return bitmapReference.get();

      try {
        final Bitmap bitmap = BitmapUtil.createScaledBitmap(context,
                                                            "file:///android_asset/" + model.getSprite(),
                                                            decodeScale);
        bitmapReference = new SoftReference<>(bitmap);
        Log.w(TAG, "onPageLoaded(" + model.getSprite() + ")");
        return bitmap;
      } catch (ExecutionException e) {
        Log.w(TAG, e);
        throw e;
      }
    }

    @Override public String toString() {
      return model.getSprite();
    }
  }
}
