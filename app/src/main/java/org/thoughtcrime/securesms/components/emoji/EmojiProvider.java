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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiDrawInfo;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.emoji.EmojiPageCache;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.util.concurrent.ExecutionException;

class EmojiProvider {

  private static final    String TAG   = Log.tag(EmojiProvider.class);
  private static final    Paint  PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

  static @Nullable EmojiParser.CandidateList getCandidates(@Nullable CharSequence text) {
    if (text == null) return null;
    return new EmojiParser(EmojiSource.getLatest().getEmojiTree()).findCandidates(text);
  }

  static  @Nullable Spannable emojify(@Nullable CharSequence text, @NonNull TextView tv) {
    if (tv.isInEditMode()) {
      return null;
    } else {
      return emojify(getCandidates(text), text, tv);
    }
  }

  static @Nullable Spannable emojify(@Nullable EmojiParser.CandidateList matches,
                                     @Nullable CharSequence text,
                                     @NonNull TextView tv)
  {
    if (matches == null || text == null || tv.isInEditMode()) return null;
    SpannableStringBuilder builder = new SpannableStringBuilder(text);

    for (EmojiParser.Candidate candidate : matches) {
      Drawable drawable = getEmojiDrawable(tv.getContext(), candidate.getDrawInfo(), tv::requestLayout);

      if (drawable != null) {
        builder.setSpan(new EmojiSpan(drawable, tv), candidate.getStartIndex(), candidate.getEndIndex(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  static @Nullable Drawable getEmojiDrawable(@NonNull Context context, @Nullable CharSequence emoji) {
    EmojiDrawInfo drawInfo = EmojiSource.getLatest().getEmojiTree().getEmoji(emoji, 0, emoji.length());
    return getEmojiDrawable(context, drawInfo, null);
  }

  /**
   * Gets an EmojiDrawable from the Page Cache
   *
   * @param context         Context object used in reading and writing from disk
   * @param drawInfo        Information about the emoji being displayed
   * @param onEmojiLoaded   Runnable which will trigger when an emoji is loaded from disk
   */
  private static @Nullable Drawable getEmojiDrawable(@NonNull Context context, @Nullable EmojiDrawInfo drawInfo, @Nullable Runnable onEmojiLoaded) {
    if (drawInfo == null) {
      return null;
    }

    final int           lowMemoryDecodeScale = DeviceProperties.isLowMemoryDevice(context) ? 2 : 1;
    final EmojiSource   source               = EmojiSource.getLatest();
    final EmojiDrawable drawable             = new EmojiDrawable(source, drawInfo, lowMemoryDecodeScale);

    EmojiPageCache.LoadResult loadResult = EmojiPageCache.INSTANCE.load(context, drawInfo.getPage(), lowMemoryDecodeScale);

    if (loadResult instanceof EmojiPageCache.LoadResult.Immediate) {
      ThreadUtil.runOnMain(() -> drawable.setBitmap(((EmojiPageCache.LoadResult.Immediate) loadResult).getBitmap()));
    } else if (loadResult instanceof EmojiPageCache.LoadResult.Async) {
      ((EmojiPageCache.LoadResult.Async) loadResult).getTask().addListener(new FutureTaskListener<Bitmap>() {
        @Override
        public void onSuccess(Bitmap result) {
          ThreadUtil.runOnMain(() -> {
            drawable.setBitmap(result);
            if (onEmojiLoaded != null) {
              onEmojiLoaded.run();
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

    return drawable;
  }

  static final class EmojiDrawable extends Drawable {
    private final float intrinsicWidth;
    private final float intrinsicHeight;
    private final Rect  emojiBounds;

    private Bitmap bmp;

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
                        emojiBounds,
                        getBounds(),
                        PAINT);
    }

    public void setBitmap(Bitmap bitmap) {
      ThreadUtil.assertMainThread();
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
