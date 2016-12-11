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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiDrawInfo;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiPageBitmap;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiTree;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

class EmojiProvider {

  private static final    String        TAG      = EmojiProvider.class.getSimpleName();
  private static volatile EmojiProvider instance = null;
  private static final    Paint         paint    = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

  private final EmojiTree emojiTree = new EmojiTree();

  private static final int EMOJI_RAW_HEIGHT = 64;
  private static final int EMOJI_RAW_WIDTH  = 64;
  private static final int EMOJI_VERT_PAD   = 0;
  private static final int EMOJI_PER_ROW    = 32;

  private final float decodeScale;
  private final float verticalPad;

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
    this.decodeScale = Math.min(1f, context.getResources().getDimension(R.dimen.emoji_drawer_size) / EMOJI_RAW_HEIGHT);
    this.verticalPad = EMOJI_VERT_PAD * this.decodeScale;

    for (EmojiPageModel page : EmojiPages.PAGES) {
      if (page.hasSpriteMap()) {
        EmojiPageBitmap pageBitmap = new EmojiPageBitmap(context, page, decodeScale);

        for (int i=0;i<page.getEmoji().length;i++) {
          emojiTree.add(page.getEmoji()[i], new EmojiDrawInfo(pageBitmap, i));
        }
      }
    }
  }

  @Nullable Spannable emojify(@Nullable CharSequence text, @NonNull TextView tv) {
    if (text == null) return null;

    List<EmojiParser.Candidate> matches = new EmojiParser(emojiTree).findCandidates(text);
    SpannableStringBuilder      builder = new SpannableStringBuilder(text);

    for (EmojiParser.Candidate candidate : matches) {
      Drawable drawable = getEmojiDrawable(candidate.getDrawInfo());

      if (drawable != null) {
        builder.setSpan(new EmojiSpan(drawable, tv), candidate.getStartIndex(), candidate.getEndIndex(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return builder;
  }

  @Nullable Drawable getEmojiDrawable(CharSequence emoji) {
    EmojiDrawInfo drawInfo = emojiTree.getEmoji(emoji, 0, emoji.length());
    return getEmojiDrawable(drawInfo);
  }

  private @Nullable Drawable getEmojiDrawable(@Nullable EmojiDrawInfo drawInfo) {
    if (drawInfo == null)  {
      return null;
    }

    final EmojiDrawable drawable = new EmojiDrawable(drawInfo, decodeScale);
    drawInfo.getPage().get().addListener(new FutureTaskListener<Bitmap>() {
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

  class EmojiDrawable extends Drawable {
    private final EmojiDrawInfo info;
    private       Bitmap        bmp;
    private       float         intrinsicWidth;
    private       float         intrinsicHeight;

    @Override
    public int getIntrinsicWidth() {
      return (int)intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
      return (int)intrinsicHeight;
    }

    EmojiDrawable(EmojiDrawInfo info, float decodeScale) {
      this.info            = info;
      this.intrinsicWidth  = EMOJI_RAW_WIDTH  * decodeScale;
      this.intrinsicHeight = EMOJI_RAW_HEIGHT * decodeScale;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
      if (bmp == null) {
        return;
      }

      final int row = info.getIndex() / EMOJI_PER_ROW;
      final int row_index = info.getIndex() % EMOJI_PER_ROW;

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

}
