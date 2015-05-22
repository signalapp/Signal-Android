package org.thoughtcrime.securesms.components.emoji;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import com.google.common.base.Optional;

public class EmojiView extends View implements Drawable.Callback {
  private String   emoji;
  private Drawable drawable;

  private final Paint paint      = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect  textBounds = new Rect();

  public EmojiView(Context context) {
    super(context);
  }

  public EmojiView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public EmojiView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public EmojiView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public void setEmoji(String emoji) {
    this.emoji    = emoji;
    this.drawable = EmojiProvider.getInstance(getContext())
                                 .getEmojiDrawable(Character.codePointAt(emoji, 0),
                                                   EmojiProvider.EMOJI_FULL);
    postInvalidate();
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
      paint.setColor(Color.BLACK);
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
