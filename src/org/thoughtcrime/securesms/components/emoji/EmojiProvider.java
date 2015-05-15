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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.SparseArray;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.ResUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiProvider {
  private static final    String                             TAG      = EmojiProvider.class.getSimpleName();
  private static volatile EmojiProvider                      instance = null;
  private static final    SparseArray<SoftReference<Bitmap>> bitmaps  = new SparseArray<>();
  private static final    Paint                              paint    = new Paint();
  static { paint.setFilterBitmap(true); }

  private final SparseArray<DrawInfo> offsets = new SparseArray<>();

  @SuppressWarnings("MalformedRegex")
  //                                                            0x20a0-0x32ff          0x1f00-0x1fff              0xfe4e5-0xfe4ee
  //                                                           |==== misc ====||======== emoticons ========||========= flags ==========|
  private static final Pattern EMOJI_RANGE = Pattern.compile("[\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83d\\udeff\\udbb9\\udce5-\\udbb9\\udcee]");

  public static final double EMOJI_HUGE     = 1.00;
  public static final double EMOJI_LARGE    = 0.75;
  public static final double EMOJI_SMALL    = 0.60;
  public static final int    EMOJI_RAW_SIZE = 128;
  public static final int    EMOJI_PER_ROW  = 16;

  private final Context                                   context;
  private final int                                       bigDrawSize;
  private final int[]                                     pages;
  private final List<Semaphore>                           pageLocks;
  private final List<Queue<WeakReference<EmojiDrawable>>> pageListeners;

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
    this.context       = context.getApplicationContext();
    this.bigDrawSize   = context.getResources().getDimensionPixelSize(R.dimen.emoji_drawer_size);
    this.pages         = ResUtil.getResourceIds(context, R.array.emoji_categories);
    this.pageLocks     = new ArrayList<>(pages.length);
    this.pageListeners = new ArrayList<>(pages.length);

    for (int i = 0; i < pages.length; i++) {
      pageListeners.add(new LinkedList<WeakReference<EmojiDrawable>>());
      pageLocks.add(new Semaphore(1));
      final int[] page = context.getResources().getIntArray(pages[i]);
      for (int j = 0; j < page.length; j++) {
        offsets.put(page[j], new DrawInfo(i, j));
      }
    }
  }

  private void notifyPageLoaded(final int page, final Bitmap bitmap) {
    Log.w(TAG, "notifyPageLoaded(" + page + ")");
    Queue<WeakReference<EmojiDrawable>>    listeners = pageListeners.get(page);
    Iterator<WeakReference<EmojiDrawable>> iterator  = listeners.iterator();
    while (iterator.hasNext()) {
      WeakReference<EmojiDrawable> weakDrawable = iterator.next();
      if (weakDrawable.get() != null) {
        Log.w(TAG, "setting bitmap");
        weakDrawable.get().onBitmapLoaded(bitmap);
      } else {
        iterator.remove();
      }
    }
  }

  private void preloadPage(final int page) {
    if (bitmaps.get(page) != null && bitmaps.get(page).get() != null) {
      notifyPageLoaded(page, bitmaps.get(page).get());
      return;
    }

    if (pageLocks.get(page).tryAcquire()) {
      new AsyncTask<Void, Void, Bitmap>() {
        @Override protected Bitmap doInBackground(Void... params) {
          try {
            Log.w(TAG, "loading page " + page);
            return loadPage(page);
          } catch (IOException ioe) {
            Log.w(TAG, ioe);
          } finally {
            pageLocks.get(page).release();
          }
          return null;
        }

        @Override protected void onPostExecute(Bitmap bitmap) {
          notifyPageLoaded(page, bitmap);
        }
      }.execute();
    }
  }

  private Bitmap loadPage(int page) throws IOException {
    if (page < 0 || page >= pages.length) {
      throw new IndexOutOfBoundsException("can't load page that doesn't exist");
    }

    if (bitmaps.get(page) != null && bitmaps.get(page).get() != null) return bitmaps.get(page).get();

    try {
      final String file = "emoji_" + page + "_wrapped.png";
      final InputStream measureStream = context.getAssets().open(file);
      final InputStream bitmapStream = context.getAssets().open(file);
      final Bitmap bitmap = BitmapUtil.createScaledBitmap(measureStream, bitmapStream, (float)bigDrawSize / (float)EMOJI_RAW_SIZE);
      bitmaps.put(page, new SoftReference<>(bitmap));
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

  public CharSequence emojify(CharSequence text, double size, Callback callback) {
    Matcher                matches = EMOJI_RANGE.matcher(text);
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    while (matches.find()) {
      int codePoint = matches.group().codePointAt(0);
      Drawable drawable = getEmojiDrawable(codePoint, size);
      if (drawable != null) {
        InvalidatingDrawableSpan emojiSpan = new InvalidatingDrawableSpan(drawable, callback);
        char[] chars = new char[matches.end() - matches.start()];
        Arrays.fill(chars, ' ');
        builder.setSpan(emojiSpan, matches.start(), matches.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  public Drawable getEmojiDrawable(int emojiCode, double size) {
    return getEmojiDrawable(offsets.get(emojiCode), size);
  }

  private Drawable getEmojiDrawable(DrawInfo drawInfo, double size) {
    if (drawInfo == null) {
      return null;
    }
    final EmojiDrawable drawable = new EmojiDrawable(drawInfo, bigDrawSize);
    drawable.setBounds(0, 0, (int)((double)bigDrawSize * size), (int)((double)bigDrawSize * size));
    pageListeners.get(drawInfo.page).add(new WeakReference<>(drawable));
    if (bitmaps.get(drawInfo.page) == null || bitmaps.get(drawInfo.page).get() == null) {
      preloadPage(drawInfo.page);
    } else {
      drawable.onBitmapLoaded(bitmaps.get(drawInfo.page).get());
    }
    return drawable;
  }

  public class EmojiDrawable extends Drawable {
    private final        int    index;
    private final        int    page;
    private final        int    emojiSize;
    private              Bitmap bmp;

    @Override public int getIntrinsicWidth() {
      return emojiSize;
    }

    @Override public int getIntrinsicHeight() {
      return emojiSize;
    }

    public EmojiDrawable(DrawInfo info, int emojiSize) {
      this.index     = info.index;
      this.page      = info.page;
      this.emojiSize = emojiSize;
    }

    @Override
    public void draw(Canvas canvas) {
      if (bmp == null) {
        return;
      }

      Rect b = copyBounds();

      final int row = index / EMOJI_PER_ROW;
      final int row_index = index % EMOJI_PER_ROW;

      canvas.drawBitmap(bmp,
                        new Rect(row_index * emojiSize,
                                 row * emojiSize,
                                 (row_index + 1) * emojiSize,
                                 (row + 1) * emojiSize),
                        b,
                        paint);
    }

    public void onBitmapLoaded(Bitmap bitmap) {
      Util.assertMainThread();
      Log.w(TAG, "onBitmapLoaded(" + page + ", " + bitmap + ")");
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

    @Override
    public String toString() {
      return "EmojiDrawable{" +
          "page=" + page +
          ", index=" + index +
          '}';
    }
  }

  class DrawInfo {
    int page;
    int index;

    public DrawInfo(final int page, final int index) {
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
}
