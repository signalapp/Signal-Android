package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

public class EmojiTextView extends AppCompatTextView {
  private final boolean scaleEmojis;
  private final float   originalFontSize;

  private CharSequence source;
  private boolean      needsEllipsizing;

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
    scaleEmojis = a.getBoolean(R.styleable.EmojiTextView_scaleEmojis, false);
    a.recycle();

    a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.textSize});
    originalFontSize = a.getDimensionPixelSize(0, 0);
    a.recycle();
  }

  @Override public void setText(@Nullable CharSequence text, BufferType type) {
    EmojiProvider provider = EmojiProvider.getInstance(getContext());
    EmojiParser.CandidateList candidates = provider.getCandidates(text);

    if (scaleEmojis && candidates != null && candidates.allEmojis) {
      int emojis = candidates.size();
      float scale = 1.0f;
      if (emojis <= 8) scale += 0.25f;
      if (emojis <= 6) scale += 0.25f;
      if (emojis <= 4) scale += 0.25f;
      if (emojis <= 2) scale += 0.25f;
      setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * scale);
    } else if (scaleEmojis) {
      setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
    }

    if (useSystemEmoji()) {
      super.setText(text, type);
      return;
    }

    source = EmojiProvider.getInstance(getContext()).emojify(candidates, text, this);
    setTextEllipsized(source);
  }

  private boolean useSystemEmoji() {
   return TextSecurePreferences.isSystemEmojiPreferred(getContext());
  }

  private void setTextEllipsized(final @Nullable CharSequence source) {
    super.setText(needsEllipsizing ? ViewUtil.ellipsize(source, this) : source, BufferType.SPANNABLE);
  }

  @Override public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int size = MeasureSpec.getSize(widthMeasureSpec);
    final int mode = MeasureSpec.getMode(widthMeasureSpec);
    if (!useSystemEmoji()                                            &&
        getEllipsize() == TruncateAt.END                             &&
        !TextUtils.isEmpty(source)                                   &&
        (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) &&
        getPaint().breakText(source, 0, source.length()-1, true, size, null) != source.length())
    {
      needsEllipsizing = true;
      FontMetricsInt font = getPaint().getFontMetricsInt();
      super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                      MeasureSpec.makeMeasureSpec(Math.abs(font.top - font.bottom), MeasureSpec.EXACTLY));
    } else {
      needsEllipsizing = false;
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (changed && !useSystemEmoji()) setTextEllipsized(source);
    super.onLayout(changed, left, top, right, bottom);
  }
}
