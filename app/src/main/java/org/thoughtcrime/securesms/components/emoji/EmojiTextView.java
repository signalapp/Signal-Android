package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Annotation;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.mention.MentionRendererDelegate;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;


public class EmojiTextView extends AppCompatTextView {

  private final boolean scaleEmojis;
  private final boolean forceCustom;

  private static final char ELLIPSIS = '…';

  private CharSequence previousText;
  private BufferType   previousBufferType;
  private float        originalFontSize;
  private boolean      useSystemEmoji;
  private boolean      sizeChangeInProgress;
  private int          maxLength;
  private CharSequence overflowText;
  private CharSequence previousOverflowText;
  private boolean      renderMentions;

  private MentionRendererDelegate mentionRendererDelegate;

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
    scaleEmojis    = a.getBoolean(R.styleable.EmojiTextView_scaleEmojis, false);
    maxLength      = a.getInteger(R.styleable.EmojiTextView_emoji_maxLength, -1);
    forceCustom    = a.getBoolean(R.styleable.EmojiTextView_emoji_forceCustom, false);
    renderMentions = a.getBoolean(R.styleable.EmojiTextView_emoji_renderMentions, true);
    a.recycle();

    a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.textSize});
    originalFontSize = a.getDimensionPixelSize(0, 0);
    a.recycle();

    if (renderMentions) {
      mentionRendererDelegate = new MentionRendererDelegate(getContext(), ContextCompat.getColor(getContext(), R.color.transparent_black_20));
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (renderMentions && getText() instanceof Spanned && getLayout() != null) {
      int checkpoint = canvas.save();
      canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
      try {
        mentionRendererDelegate.draw(canvas, (Spanned) getText(), getLayout());
      } finally {
        canvas.restoreToCount(checkpoint);
      }
    }
    super.onDraw(canvas);
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

    if (getLayoutParams() != null && getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
      requestLayout();
    }
  }

  public void setOverflowText(@Nullable CharSequence overflowText) {
    this.overflowText = overflowText;
    setText(previousText, BufferType.SPANNABLE);
  }

  private void ellipsizeAnyTextForMaxLength() {
    if (maxLength > 0 && getText().length() > maxLength + 1) {
      SpannableStringBuilder newContent = new SpannableStringBuilder();

      CharSequence shortenedText = getText().subSequence(0, maxLength);
      if (shortenedText instanceof Spanned) {
        Spanned          spanned            = (Spanned) shortenedText;
        List<Annotation> mentionAnnotations = MentionAnnotation.getMentionAnnotations(spanned, maxLength - 1, maxLength);
        if (!mentionAnnotations.isEmpty()) {
          shortenedText = shortenedText.subSequence(0, spanned.getSpanStart(mentionAnnotations.get(0)));
        }
      }

      newContent.append(shortenedText)
                .append(ELLIPSIS)
                .append(Util.emptyIfNull(overflowText));

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
   return !forceCustom && TextSecurePreferences.isSystemEmojiPreferred(getContext());
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

  public void setMentionBackgroundTint(@ColorInt int mentionBackgroundTint) {
    if (renderMentions) {
      mentionRendererDelegate.setTint(mentionBackgroundTint);
    }
  }
}
