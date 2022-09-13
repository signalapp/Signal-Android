package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiDrawInfo;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.emoji.EmojiPageCache;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.emoji.JumboEmoji;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.FutureTaskListener;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmojiProvider {

  private static final    String TAG   = Log.tag(EmojiProvider.class);
  private static final    Paint  PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

  public static @Nullable EmojiParser.CandidateList getCandidates(@Nullable CharSequence text) {
    if (text == null) return null;
    return new EmojiParser(EmojiSource.getLatest().getEmojiTree()).findCandidates(text);
  }

  static  @Nullable Spannable emojify(@Nullable CharSequence text, @NonNull TextView tv, boolean jumboEmoji) {
    if (tv.isInEditMode()) {
      return null;
    } else {
      return emojify(getCandidates(text), text, tv, jumboEmoji);
    }
  }

  static @Nullable Spannable emojify(@Nullable EmojiParser.CandidateList matches,
                                     @Nullable CharSequence text,
                                     @NonNull TextView tv,
                                     boolean jumboEmoji)
  {
    if (matches == null || text == null || tv.isInEditMode()) return null;
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    for (EmojiParser.Candidate candidate : matches) {
      Drawable drawable = getEmojiDrawable(tv.getContext(), candidate.getDrawInfo(), tv::requestLayout, jumboEmoji);

      if (drawable != null) {
        builder.setSpan(new EmojiSpan(drawable, tv), candidate.getStartIndex(), candidate.getEndIndex(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  public static @Nullable Spannable emojify(@NonNull Context context,
                                            @Nullable EmojiParser.CandidateList matches,
                                            @Nullable CharSequence text,
                                            @NonNull Paint paint,
                                            boolean synchronous,
                                            boolean jumboEmoji)
  {
    if (matches == null || text == null) return null;
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    for (EmojiParser.Candidate candidate : matches) {
      Drawable drawable;
      if (synchronous) {
        drawable = getEmojiDrawableSync(context, candidate.getDrawInfo(), jumboEmoji);
      } else {
        drawable = getEmojiDrawable(context, candidate.getDrawInfo(), null, jumboEmoji);
      }

      if (drawable != null) {
        builder.setSpan(new EmojiSpan(context, drawable, paint), candidate.getStartIndex(), candidate.getEndIndex(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  static @Nullable Drawable getEmojiDrawable(@NonNull Context context, @Nullable CharSequence emoji) {
    return getEmojiDrawable(context, emoji, false);
  }

  static @Nullable Drawable getEmojiDrawable(@NonNull Context context, @Nullable CharSequence emoji, boolean jumboEmoji) {
    if (TextUtils.isEmpty(emoji)) {
      return null;
    }

    EmojiDrawInfo drawInfo = EmojiSource.getLatest().getEmojiTree().getEmoji(emoji, 0, emoji.length());
    return getEmojiDrawable(context, drawInfo, null, jumboEmoji);
  }

  /**
   * Gets an EmojiDrawable from the Page Cache
   *
   * @param context         Context object used in reading and writing from disk
   * @param drawInfo        Information about the emoji being displayed
   * @param onEmojiLoaded   Runnable which will trigger when an emoji is loaded from disk
   */
  private static @Nullable Drawable getEmojiDrawable(@NonNull Context context, @Nullable EmojiDrawInfo drawInfo, @Nullable Runnable onEmojiLoaded, boolean jumboEmoji) {
    if (drawInfo == null) {
      return null;
    }

    if (SignalStore.settings().isPreferSystemEmoji()) {
      return new SystemEmojiDrawable(drawInfo.getEmoji());
    }

    final int           lowMemoryDecodeScale = DeviceProperties.isLowMemoryDevice(context) ? 2 : 1;
    final EmojiSource   source               = EmojiSource.getLatest();
    final EmojiDrawable drawable             = new EmojiDrawable(source, drawInfo, lowMemoryDecodeScale);
    final AtomicBoolean jumboLoaded          = new AtomicBoolean(false);

    EmojiPageCache.LoadResult loadResult = EmojiPageCache.INSTANCE.load(context, drawInfo.getPage(), lowMemoryDecodeScale);

    if (loadResult instanceof EmojiPageCache.LoadResult.Immediate) {
      ThreadUtil.runOnMain(() -> drawable.setBitmap(((EmojiPageCache.LoadResult.Immediate) loadResult).getBitmap()));
    } else if (loadResult instanceof EmojiPageCache.LoadResult.Async) {
      ((EmojiPageCache.LoadResult.Async) loadResult).getTask().addListener(new FutureTaskListener<Bitmap>() {
        @Override
        public void onSuccess(Bitmap result) {
          ThreadUtil.runOnMain(() -> {
            if (!jumboLoaded.get()) {
              drawable.setBitmap(result);
              if (onEmojiLoaded != null) {
                onEmojiLoaded.run();
              }
            }
          });
        }

        @Override
        public void onFailure(ExecutionException exception) {
          Log.d(TAG, "Failed to load emoji bitmap resource", exception);
        }
      });
    } else {
      throw new IllegalStateException("Unexpected subclass " + loadResult.getClass());
    }

    if (jumboEmoji && drawInfo.getJumboSheet() != null) {
      JumboEmoji.LoadResult result = JumboEmoji.loadJumboEmoji(context, drawInfo);
      if (result instanceof JumboEmoji.LoadResult.Immediate) {
        ThreadUtil.runOnMain(() -> {
          jumboLoaded.set(true);
          drawable.setSingleBitmap(((JumboEmoji.LoadResult.Immediate) result).getBitmap());
        });
      } else if (result instanceof JumboEmoji.LoadResult.Async) {
        ((JumboEmoji.LoadResult.Async) result).getTask().addListener(new FutureTaskListener<Bitmap>() {
          @Override
          public void onSuccess(Bitmap result) {
            ThreadUtil.runOnMain(() -> {
              jumboLoaded.set(true);
              drawable.setSingleBitmap(result);
              if (onEmojiLoaded != null) {
                onEmojiLoaded.run();
              }
            });
          }

          @Override
          public void onFailure(ExecutionException exception) {
            if (exception.getCause() instanceof JumboEmoji.CannotAutoDownload) {
              Log.i(TAG, "Download restrictions are preventing jumbomoji use");
            } else {
              Log.d(TAG, "Failed to load jumbo emoji bitmap resource", exception);
            }
          }
        });
      }

      return drawable;
    }

    return drawable;
  }

  /**
   * Gets an EmojiDrawable from the Page Cache synchronously
   *
   * @param context         Context object used in reading and writing from disk
   * @param drawInfo        Information about the emoji being displayed
   */
  private static @Nullable Drawable getEmojiDrawableSync(@NonNull Context context, @Nullable EmojiDrawInfo drawInfo, boolean jumboEmoji) {
    ThreadUtil.assertNotMainThread();
    if (drawInfo == null) {
      return null;
    }

    if (SignalStore.settings().isPreferSystemEmoji()) {
      return new SystemEmojiDrawable(drawInfo.getEmoji());
    }

    final int           lowMemoryDecodeScale = DeviceProperties.isLowMemoryDevice(context) ? 2 : 1;
    final EmojiSource   source               = EmojiSource.getLatest();
    final EmojiDrawable drawable             = new EmojiDrawable(source, drawInfo, lowMemoryDecodeScale);

    Bitmap bitmap = null;

    if (jumboEmoji && drawInfo.getJumboSheet() != null) {
      JumboEmoji.LoadResult result = JumboEmoji.loadJumboEmoji(context, drawInfo);
      if (result instanceof JumboEmoji.LoadResult.Immediate) {
        bitmap = ((JumboEmoji.LoadResult.Immediate) result).getBitmap();
      } else if (result instanceof JumboEmoji.LoadResult.Async) {
        try {
          bitmap = ((JumboEmoji.LoadResult.Async) result).getTask().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
          if (exception.getCause() instanceof JumboEmoji.CannotAutoDownload) {
            Log.i(TAG, "Download restrictions are preventing jumbomoji use");
          } else {
            Log.d(TAG, "Failed to load jumbo emoji bitmap resource", exception);
          }
        }
      }

      if (bitmap != null) {
        drawable.setSingleBitmap(bitmap);
      }
    }

    if (!jumboEmoji || bitmap == null) {
      EmojiPageCache.LoadResult loadResult = EmojiPageCache.INSTANCE.load(context, drawInfo.getPage(), lowMemoryDecodeScale);

      if (loadResult instanceof EmojiPageCache.LoadResult.Immediate) {
        Log.d(TAG, "Cached emoji page: " + drawInfo.getPage().getUri().toString());
        bitmap = ((EmojiPageCache.LoadResult.Immediate) loadResult).getBitmap();
      } else if (loadResult instanceof EmojiPageCache.LoadResult.Async) {
        Log.d(TAG, "Loading emoji page: " + drawInfo.getPage().getUri().toString());
        try {
          bitmap = ((EmojiPageCache.LoadResult.Async) loadResult).getTask().get(2, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
          Log.d(TAG, "Failed to load emoji bitmap resource", exception);
        }
      } else {
        throw new IllegalStateException("Unexpected subclass " + loadResult.getClass());
      }

      drawable.setBitmap(bitmap);
    }

    return drawable;
  }

  static final class EmojiDrawable extends Drawable {
    private final float intrinsicWidth;
    private final float intrinsicHeight;
    private final Rect  emojiBounds;

    private Bitmap  bmp;
    private boolean isSingleBitmap;

    @Override
    public int getIntrinsicWidth() {
      return (int) intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
      return (int) intrinsicHeight;
    }

    EmojiDrawable(@NonNull EmojiSource source, @NonNull EmojiDrawInfo info, int lowMemoryDecodeScale) {
      this.intrinsicWidth  = (source.getMetrics().getRawWidth() * source.getDecodeScale()) / lowMemoryDecodeScale;
      this.intrinsicHeight = (source.getMetrics().getRawHeight() * source.getDecodeScale()) / lowMemoryDecodeScale;

      final int glyphWidth  = (int) (intrinsicWidth);
      final int glyphHeight = (int) (intrinsicHeight);
      final int index       = info.getIndex();
      final int emojiPerRow = source.getMetrics().getPerRow();
      final int xStart      = (index % emojiPerRow) * glyphWidth;
      final int yStart      = (index / emojiPerRow) * glyphHeight;

      this.emojiBounds = new Rect(xStart + 1,
                                  yStart + 1,
                                  xStart + glyphWidth - 1,
                                  yStart + glyphHeight - 1);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
      if (bmp == null) {
        return;
      }

      canvas.drawBitmap(bmp,
                        isSingleBitmap ? null : emojiBounds,
                        getBounds(),
                        PAINT);
    }

    public void setBitmap(Bitmap bitmap) {
      setBitmap(bitmap, false);
    }

    public void setSingleBitmap(Bitmap bitmap) {
      setBitmap(bitmap, true);
    }

    private void setBitmap(Bitmap bitmap, boolean isSingleBitmap) {
      this.isSingleBitmap = isSingleBitmap;
      if (bmp == null || !bmp.sameAs(bitmap)) {
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
}
