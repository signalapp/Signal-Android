package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.SparseArray;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiProvider {
  private static final    String        TAG      = EmojiProvider.class.getSimpleName();
  private static volatile EmojiProvider instance = null;
  private static final    Paint         paint    = new Paint();
  static { paint.setFilterBitmap(true); }

  private final SparseArray<DrawInfo> offsets = new SparseArray<>();

  @SuppressWarnings("MalformedRegex")
  //                                                            0x20a0-0x32ff          0x1f00-0x1fff              0xfe4e5-0xfe4ee
  //                                                           |==== misc ====||======== emoticons ========||========= flags ==========|
  private static final Pattern EMOJI_RANGE = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]");

  public static final double EMOJI_FULL       = 1.00;
  public static final double EMOJI_SMALL      = 0.50;
  public static final int    EMOJI_RAW_HEIGHT = 128;
  public static final int    EMOJI_RAW_WIDTH  = 136;
  public static final int    EMOJI_VERT_PAD   = 8;
  public static final int    EMOJI_PER_ROW    = 15;

  private final Context context;
  private final double  drawWidth;
  private final double  drawHeight;
  private final double  verticalPad;
  private final Handler handler = new Handler(Looper.getMainLooper());

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
    int[] pages = ResUtil.getResourceIds(context, R.array.emoji_categories);

    this.context     = context.getApplicationContext();
    this.drawHeight  = context.getResources().getDimension(R.dimen.emoji_drawer_size);
    this.drawWidth   = drawHeight * ((double)EMOJI_RAW_WIDTH) / EMOJI_RAW_HEIGHT;
    this.verticalPad = EMOJI_VERT_PAD * drawHeight / EMOJI_RAW_HEIGHT;
    Log.w(TAG, "draw size: " + drawWidth + "x" + drawHeight);
    for (int i = 0; i < pages.length; i++) {
      final EmojiPageBitmap page = new EmojiPageBitmap(i);
      final int[] codePoints = context.getResources().getIntArray(pages[i]);
      for (int j = 0; j < codePoints.length; j++) {
        offsets.put(codePoints[j], new DrawInfo(page, j));
      }
    }
  }

  public CharSequence emojify(CharSequence text, double size, Callback callback) {
    Matcher                matches = EMOJI_RANGE.matcher(text);
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    while (matches.find()) {
      int codePoint = matches.group().codePointAt(0);
      Drawable drawable = getEmojiDrawable(codePoint, size);
      if (drawable != null) {
        builder.setSpan(new InvalidatingDrawableSpan(drawable, callback), matches.start(), matches.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  public Drawable getEmojiDrawable(int emojiCode, double size) {
    return getEmojiDrawable(offsets.get(emojiCode), size);
  }

  private Drawable getEmojiDrawable(DrawInfo drawInfo, double size) {
    if (drawInfo == null) return null;

    final EmojiDrawable drawable = new EmojiDrawable(drawInfo, drawWidth, drawHeight);
    drawable.setBounds(0, 0, (int)(drawWidth * size), (int)(drawHeight * size));
    drawInfo.page.get().addListener(new FutureTaskListener<Bitmap>() {
      @Override public void onSuccess(final Bitmap result) {
        handler.post(new Runnable() {
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
    private final int    index;
    private final double width;
    private final double height;
    private       Bitmap bmp;

    @Override public int getIntrinsicWidth() {
      return (int)width;
    }

    @Override public int getIntrinsicHeight() {
      return (int)height;
    }

    public EmojiDrawable(DrawInfo info, double width, double height) {
      this.index  = info.index;
      this.width  = width;
      this.height = height;
    }

    @Override
    public void draw(Canvas canvas) {
      if (bmp == null) return;

      Rect b = copyBounds();

      final int row = index / EMOJI_PER_ROW;
      final int row_index = index % EMOJI_PER_ROW;

      canvas.drawBitmap(bmp,
                        new Rect((int)(row_index * width),
                                 (int)(row * height + row * verticalPad),
                                 (int)((row_index + 1) * width),
                                 (int)((row + 1) * height + row * verticalPad)),
                        b,
                        paint);
    }

    public void setBitmap(Bitmap bitmap) {
      Util.assertMainThread();
      bmp = bitmap;
      invalidateSelf();
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
    private int                          page;
    private SoftReference<Bitmap>        bitmapReference;
    private ListenableFutureTask<Bitmap> task;

    public EmojiPageBitmap(int page) {
      this.page = page;
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
              Log.w(TAG, "loading page " + page);
              return loadPage();
            } catch (IOException ioe) {
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

    private Bitmap loadPage() throws IOException {
      if (bitmapReference != null && bitmapReference.get() != null) return bitmapReference.get();

      try {
        final String      file          = "emoji-" + page + ".png";
        final InputStream measureStream = context.getAssets().open(file);
        final InputStream bitmapStream  = context.getAssets().open(file);
        final Bitmap      bitmap        = BitmapUtil.createScaledBitmap(measureStream, bitmapStream, (float) drawHeight / (float) EMOJI_RAW_HEIGHT);
        bitmapReference = new SoftReference<>(bitmap);
        Log.w(TAG, "onPageLoaded(" + page + ")");
        return bitmap;
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        throw ioe;
      } catch (BitmapDecodingException bde) {
        Log.w(TAG, bde);
        throw new AssertionError("emoji sprite asset is corrupted or android decoding is broken");
      }
    }
  }
}
