package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;


public class EmojiTextView extends AppCompatTextView {

  private final boolean scaleEmojis;

  private static final char ELLIPSIS = '…';

  private CharSequence previousText;
  private BufferType   previousBufferType;
  private float        originalFontSize;
  private boolean      useSystemEmoji;
  private boolean      sizeChangeInProgress;
  private int          maxLength;
  private CharSequence overflowText;
  private CharSequence previousOverflowText;

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
    maxLength   = a.getInteger(R.styleable.EmojiTextView_emoji_maxLength, -1);
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

    if (unchanged(text, overflowText, type)) {
      return;
    }

    previousText         = text;
    previousOverflowText = overflowText;
    previousBufferType   = type;
    useSystemEmoji       = useSystemEmoji();

    if (useSystemEmoji || candidates == null || candidates.size() == 0) {
      super.setText(new SpannableStringBuilder(Optional.fromNullable(text).or("")).append(Optional.fromNullable(overflowText).or("")), BufferType.NORMAL);

      if (getEllipsize() == TextUtils.TruncateAt.END && maxLength > 0) {
        ellipsizeAnyTextForMaxLength();
      }
    } else {
      CharSequence emojified = provider.emojify(candidates, text, this);
      super.setText(new SpannableStringBuilder(emojified).append(Optional.fromNullable(overflowText).or("")), BufferType.SPANNABLE);

      // Android fails to ellipsize spannable strings. (https://issuetracker.google.com/issues/36991688)
      // We ellipsize them ourselves by manually truncating the appropriate section.
      if (getEllipsize() == TextUtils.TruncateAt.END) {
        if (maxLength > 0) {
          ellipsizeAnyTextForMaxLength();
        } else {
          ellipsizeEmojiTextForMaxLines();
        }
      }
    }
  }

  public void setOverflowText(@Nullable CharSequence overflowText) {
    this.overflowText = overflowText;
    setText(previousText, BufferType.SPANNABLE);
  }

  private void ellipsizeAnyTextForMaxLength() {
    if (maxLength > 0 && getText().length() > maxLength + 1) {
      SpannableStringBuilder newContent = new SpannableStringBuilder();
      newContent.append(getText().subSequence(0, maxLength)).append(ELLIPSIS).append(Optional.fromNullable(overflowText).or(""));

      EmojiParser.CandidateList newCandidates = EmojiProvider.getInstance(getContext()).getCandidates(newContent);

      if (useSystemEmoji || newCandidates == null || newCandidates.size() == 0) {
        super.setText(newContent, BufferType.NORMAL);
      } else {
        CharSequence emojified = EmojiProvider.getInstance(getContext()).emojify(newCandidates, newContent, this);
        super.setText(emojified, BufferType.SPANNABLE);
      }
    }
  }

  private void ellipsizeEmojiTextForMaxLines() {
    post(() -> {
      if (getLayout() == null) {
        ellipsizeEmojiTextForMaxLines();
        return;
      }

      int maxLines = TextViewCompat.getMaxLines(EmojiTextView.this);
      if (maxLines <= 0 && maxLength < 0) {
        return;
      }

      int lineCount = getLineCount();
      if (lineCount > maxLines) {
        int overflowStart = getLayout().getLineStart(maxLines - 1);
        CharSequence overflow = getText().subSequence(overflowStart, getText().length());
        CharSequence ellipsized = TextUtils.ellipsize(overflow, getPaint(), getWidth(), TextUtils.TruncateAt.END);

        SpannableStringBuilder newContent = new SpannableStringBuilder();
        newContent.append(getText().subSequence(0, overflowStart))
                  .append(ellipsized.subSequence(0, ellipsized.length()))
                  .append(Optional.fromNullable(overflowText).or(""));

        EmojiParser.CandidateList newCandidates = EmojiProvider.getInstance(getContext()).getCandidates(newContent);
        CharSequence              emojified     = EmojiProvider.getInstance(getContext()).emojify(newCandidates, newContent, this);

        super.setText(emojified, BufferType.SPANNABLE);
      }
    });
  }

  private boolean unchanged(CharSequence text, CharSequence overflowText, BufferType bufferType) {
    return Util.equals(previousText, text)                 &&
           Util.equals(previousOverflowText, overflowText) &&
           Util.equals(previousBufferType, bufferType)     &&
           useSystemEmoji == useSystemEmoji()              &&
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
