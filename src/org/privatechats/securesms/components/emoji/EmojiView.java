package org.privatechats.securesms.components.emoji;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import org.privatechats.securesms.R;
import org.privatechats.securesms.util.ResUtil;

public class EmojiView extends View implements Drawable.Callback {
  private String   emoji;
  private Drawable drawable;

  private final Paint paint      = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Rect  textBounds = new Rect();

  public EmojiView(Context context) {
    this(context, null);
  }

  public EmojiView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setEmoji(String emoji) {
    this.emoji    = emoji;
    this.drawable = EmojiProvider.getInstance(getContext())
                                 .getEmojiDrawable(Character.codePointAt(emoji, 0));
    postInvalidate();
  }

  public String getEmoji() {
    return emoji;
  }

  @Override protected void onDraw(Canvas canvas) {
    if (drawable != null) {
      drawable.setBounds(getPaddingLeft(),
                         getPaddingTop(),
                         getWidth() - getPaddingRight(),
                         getHeight() - getPaddingBottom());
      drawable.setCallback(this);
      drawable.draw(canvas);
    } else {
      float targetFontSize = 0.75f * getHeight() - getPaddingTop() - getPaddingBottom();
      paint.setTextSize(targetFontSize);
      paint.setColor(ResUtil.getColor(getContext(), R.attr.emoji_text_color));
      paint.getTextBounds(emoji, 0, emoji.length(), textBounds);
      float overflow = textBounds.width() / (getWidth() - getPaddingLeft() - getPaddingRight());
      if (overflow > 1f) {
        paint.setTextSize(targetFontSize / overflow);
      }
      canvas.drawText(emoji, 0.5f * (getWidth() - textBounds.width()), 0.5f * (getHeight() + textBounds.height()), paint);
    }
  }

  @Override public void invalidateDrawable(@NonNull Drawable drawable) {
    super.invalidateDrawable(drawable);
    postInvalidate();
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
