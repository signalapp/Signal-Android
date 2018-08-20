package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.AppCompatTextView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;


public class EmojiTextView extends AppCompatTextView {

  private final boolean scaleEmojis;

  private CharSequence previousText;
  private BufferType   previousBufferType;
  private float        originalFontSize;
  private boolean      useSystemEmoji;
  private boolean      sizeChangeInProgress;

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
    EmojiProvider             provider   = EmojiProvider.getInstance(getContext());
    EmojiParser.CandidateList candidates = provider.getCandidates(text);

    if (scaleEmojis && candidates != null && candidates.allEmojis) {
      int   emojis = candidates.size();
      float scale  = 1.0f;

      if (emojis <= 8) scale += 0.25f;
      if (emojis <= 6) scale += 0.25f;
      if (emojis <= 4) scale += 0.25f;
      if (emojis <= 2) scale += 0.25f;

      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale);
    } else if (scaleEmojis) {
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
    }

    if (unchanged(text, type)) {
      return;
    }

    previousText       = text;
    previousBufferType = type;
    useSystemEmoji     = useSystemEmoji();

    if (useSystemEmoji || candidates == null || candidates.size() == 0) {
      super.setText(text, BufferType.NORMAL);
      return;
    }

    CharSequence emojified = provider.emojify(candidates, text, this);
    super.setText(emojified, BufferType.SPANNABLE);

    // Android fails to ellipsize spannable strings. (https://issuetracker.google.com/issues/36991688)
    // We ellipsize them ourselves by manually truncating the appropriate section.
    if (getEllipsize() == TextUtils.TruncateAt.END) {
      ellipsize();
    }
  }

  private void ellipsize() {
    post(() -> {
      if (getLayout() == null) {
        ellipsize();
        return;
      }

      int maxLines = TextViewCompat.getMaxLines(EmojiTextView.this);
      if (maxLines <= 0) {
        return;
      }
      
      int lineCount = getLineCount();
      if (lineCount > maxLines) {
        int overflowStart = getLayout().getLineStart(maxLines - 1);
        CharSequence overflow = getText().subSequence(overflowStart, getText().length());
        CharSequence ellipsized = TextUtils.ellipsize(overflow, getPaint(), getWidth(), TextUtils.TruncateAt.END);

        SpannableStringBuilder newContent = new SpannableStringBuilder();
        newContent.append(getText().subSequence(0, overflowStart))
            .append(ellipsized.subSequence(0, ellipsized.length()));

        EmojiParser.CandidateList newCandidates = EmojiProvider.getInstance(getContext()).getCandidates(newContent);
        CharSequence              emojified     = EmojiProvider.getInstance(getContext()).emojify(newCandidates, newContent, this);

        super.setText(emojified, BufferType.SPANNABLE);
      }
    });
  }

  private boolean unchanged(CharSequence text, BufferType bufferType) {
    return Util.equals(previousText, text)             &&
           Util.equals(previousBufferType, bufferType) &&
           useSystemEmoji == useSystemEmoji()          &&
           !sizeChangeInProgress;
  }

  private boolean useSystemEmoji() {
   return TextSecurePreferences.isSystemEmojiPreferred(getContext());
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    if (!sizeChangeInProgress) {
      sizeChangeInProgress = true;
      setText(previousText, previousBufferType);
      sizeChangeInProgress = false;
    }
  }

  @Override
  public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  @Override
  public void setTextSize(float size) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
  }

  @Override
  public void setTextSize(int unit, float size) {
    this.originalFontSize = TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics());
    super.setTextSize(unit, size);
  }
}
