package org.thoughtcrime.securesms.components.emoji;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Annotation;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.method.TransformationMethod;
import android.text.style.CharacterStyle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewKt;
import androidx.core.widget.TextViewCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.mention.MentionRendererDelegate;
import org.thoughtcrime.securesms.components.spoiler.SpoilerRendererDelegate;
import org.thoughtcrime.securesms.conversation.MessageStyler;
import org.thoughtcrime.securesms.emoji.JumboEmoji;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import kotlin.Unit;


public class EmojiTextView extends AppCompatTextView {

  private final boolean scaleEmojis;

  private static final char  ELLIPSIS        = '…';
  private static final float JUMBOMOJI_SCALE = 0.8f;

  private CharSequence           previousText;
  private BufferType             previousBufferType;
  private TransformationMethod   previousTransformationMethod;
  private float                  originalFontSize;
  private boolean                useSystemEmoji;
  private boolean                sizeChangeInProgress;
  private int                    maxLength;
  private CharSequence           overflowText;
  private CharSequence           previousOverflowText;
  private boolean                renderMentions;
  private boolean                measureLastLine;
  private int                    lastLineWidth = -1;
  private TextDirectionHeuristic textDirection;
  private boolean                isJumbomoji;
  private boolean                forceJumboEmoji;
  private boolean                renderSpoilers;
  private boolean                shrinkWrap;

  private MentionRendererDelegate mentionRendererDelegate;
  private SpoilerRendererDelegate spoilerRendererDelegate;

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EmojiTextView, 0, 0);
    scaleEmojis     = a.getBoolean(R.styleable.EmojiTextView_scaleEmojis, false);
    maxLength       = a.getInteger(R.styleable.EmojiTextView_emoji_maxLength, -1);
    renderMentions  = a.getBoolean(R.styleable.EmojiTextView_emoji_renderMentions, true);
    measureLastLine = a.getBoolean(R.styleable.EmojiTextView_measureLastLine, false);
    forceJumboEmoji = a.getBoolean(R.styleable.EmojiTextView_emoji_forceJumbo, false);
    renderSpoilers  = a.getBoolean(R.styleable.EmojiTextView_emoji_renderSpoilers, false);
    shrinkWrap      = a.getBoolean(R.styleable.EmojiTextView_emoji_shrinkWrap, false);
    a.recycle();

    a                = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.textSize });
    originalFontSize = a.getDimensionPixelSize(0, 0);
    a.recycle();

    if (renderMentions) {
      mentionRendererDelegate = new MentionRendererDelegate(getContext(), ContextCompat.getColor(getContext(), R.color.transparent_black_20));
    }

    if (renderSpoilers) {
      spoilerRendererDelegate = new SpoilerRendererDelegate(this);
    }

    textDirection = getLayoutDirection() == LAYOUT_DIRECTION_LTR ? TextDirectionHeuristics.FIRSTSTRONG_RTL : TextDirectionHeuristics.ANYRTL_LTR;

    setEmojiCompatEnabled(useSystemEmoji());
  }

  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
    setText(getText());
  }

  @Override
  protected void onDraw(Canvas canvas) {
    boolean hasSpannedText = getText() instanceof Spanned;
    boolean hasLayout      = getLayout() != null;

    if (hasSpannedText && hasLayout) {
      drawSpecialRenderers(canvas, mentionRendererDelegate, spoilerRendererDelegate);
    }

    super.onDraw(canvas);

    if (hasSpannedText && !hasLayout && getLayout() != null) {
      drawSpecialRenderers(canvas, null, spoilerRendererDelegate);
    }
  }

  private void drawSpecialRenderers(@NonNull Canvas canvas, @Nullable MentionRendererDelegate mentionDelegate, @Nullable SpoilerRendererDelegate spoilerDelegate) {
    int checkpoint = canvas.save();
    canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
    try {
      if (mentionDelegate != null) {
        mentionDelegate.draw(canvas, (Spanned) getText(), getLayout());
      }
      if (spoilerDelegate != null) {
        spoilerDelegate.draw(canvas, (Spanned) getText(), getLayout());
      }
    } finally {
      canvas.restoreToCount(checkpoint);
    }
  }

  @Override
  public void setText(@Nullable CharSequence text, BufferType type) {
    EmojiParser.CandidateList candidates = isInEditMode() ? null : EmojiProvider.getCandidates(text);

    if (scaleEmojis &&
        candidates != null &&
        candidates.allEmojis &&
        (candidates.hasJumboForAll() || JumboEmoji.canDownloadJumbo(getContext())) &&
        (text instanceof Spanned && !MessageStyler.hasStyling((Spanned) text)))
    {
      int   emojis = candidates.size();
      float scale  = 1.0f;

      if (emojis <= 5) scale += JUMBOMOJI_SCALE;
      if (emojis <= 4) scale += JUMBOMOJI_SCALE;
      if (emojis <= 2) scale += JUMBOMOJI_SCALE;

      isJumbomoji = scale > 1.0f;
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale);
    } else if (scaleEmojis) {
      isJumbomoji = false;
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
    }

    if (unchanged(text, overflowText, type)) {
      return;
    }

    previousText                 = text;
    previousOverflowText         = overflowText;
    previousBufferType           = type;
    useSystemEmoji               = useSystemEmoji();
    previousTransformationMethod = getTransformationMethod();

    Spannable textToSet;
    if (useSystemEmoji || candidates == null || candidates.size() == 0) {
      textToSet = new SpannableStringBuilder(Optional.ofNullable(text).orElse(""));
    } else {
      textToSet = new SpannableStringBuilder(EmojiProvider.emojify(candidates, text, this, isJumbomoji || forceJumboEmoji));
    }

    super.setText(textToSet, BufferType.SPANNABLE);

    // Android fails to ellipsize spannable strings. (https://issuetracker.google.com/issues/36991688)
    // We ellipsize them ourselves by manually truncating the appropriate section.
    if (getText() != null && getText().length() > 0 && isEllipsizedAtEnd()) {
      if (getMaxLines() > 0 && getMaxLines() != Integer.MAX_VALUE) {
        ellipsizeEmojiTextForMaxLines();
      } else if (maxLength > 0) {
        ellipsizeAnyTextForMaxLength();
      }
    }

    if (getLayoutParams() != null && getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
      requestLayout();
    }
  }

  /**
   * Used to determine whether to apply custom ellipsizing logic without necessarily having the
   * ellipsize property set. This allows us to work around implementations of Layout which apply an
   * ellipsis even when maxLines is not set.
   */
  private boolean isEllipsizedAtEnd() {
    return getEllipsize() == TextUtils.TruncateAt.END ||
           (getMaxLines() > 0 && getMaxLines() < Integer.MAX_VALUE) ||
           maxLength > 0;
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    widthMeasureSpec = applyWidthMeasureRoundingFix(widthMeasureSpec);

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int mode = MeasureSpec.getMode(widthMeasureSpec);
    if (shrinkWrap && getLayout() != null && mode == MeasureSpec.AT_MOST) {
      Layout layout = getLayout();

      float maxLineWidth = 0f;
      for (int i = 0; i < layout.getLineCount(); i++) {
        if (layout.getLineWidth(i) > maxLineWidth) {
          maxLineWidth = layout.getLineWidth(i);
        }
      }

      int desiredWidth = (int) maxLineWidth + getPaddingLeft() + getPaddingRight();
      if (getMeasuredWidth() > desiredWidth) {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(desiredWidth, mode);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    }

    CharSequence text = getText();
    if (getLayout() == null || !measureLastLine || text == null || text.length() == 0) {
      lastLineWidth = -1;
    } else {
      Layout layout = getLayout();
      text = layout.getText();

      int lines = layout.getLineCount();
      int start = layout.getLineStart(lines - 1);

      if ((getLayoutDirection() == LAYOUT_DIRECTION_LTR && textDirection.isRtl(text, 0, text.length())) ||
          (getLayoutDirection() == LAYOUT_DIRECTION_RTL && !textDirection.isRtl(text, 0, text.length())))
      {
        lastLineWidth = getMeasuredWidth();
      } else {
        lastLineWidth = (int) getPaint().measureText(text, start, text.length());
      }
    }
  }

  /**
   * Starting from API 30, there can be a rounding error in text layout when a non-zero letter
   * spacing is used. This causes a line break to be inserted where there shouldn't be one. Force
   * the width to be larger to work around this problem.
   * https://issuetracker.google.com/issues/173574230
   *
   * @param widthMeasureSpec the original measure spec passed to {@link #onMeasure(int, int)}
   * @return the measure spec with the workaround, or the original one.
   */
  private int applyWidthMeasureRoundingFix(int widthMeasureSpec) {
    if (Build.VERSION.SDK_INT >= 30 && getLetterSpacing() > 0) {
      CharSequence text = getText();
      if (text != null) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);

        float measuredTextWidth = hasMetricAffectingSpan(text) ? Layout.getDesiredWidth(text, getPaint()) : getLongestLineWidth(text);
        int   desiredWidth      = (int) measuredTextWidth + getPaddingLeft() + getPaddingRight();

        if (widthSpecMode == MeasureSpec.AT_MOST && desiredWidth < widthSpecSize) {
          return MeasureSpec.makeMeasureSpec(desiredWidth + 1, MeasureSpec.EXACTLY);
        }
      }
    }

    return widthMeasureSpec;
  }

  private boolean hasMetricAffectingSpan(@NonNull CharSequence text) {
    if (!(text instanceof Spanned)) {
      return false;
    }

    return ((Spanned) text).nextSpanTransition(-1, text.length(), CharacterStyle.class) != text.length();
  }

  private float getLongestLineWidth(@NonNull CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return 0f;
    }

    long maxLines = getMaxLines() > 0 ? getMaxLines() : Long.MAX_VALUE;

    return Arrays.stream(text.toString().split("\n"))
                 .limit(maxLines)
                 .map(s -> getPaint().measureText(s, 0, s.length()))
                 .max(Float::compare)
                 .orElse(0f);
  }

  public int getLastLineWidth() {
    return lastLineWidth;
  }

  public boolean isSingleLine() {
    return getLayout() != null && getLayout().getLineCount() == 1;
  }

  public boolean isJumbomoji() {
    return isJumbomoji;
  }

  public void setOverflowText(@Nullable CharSequence overflowText) {
    this.overflowText = overflowText;
    setText(previousText, BufferType.SPANNABLE);
  }

  @SuppressLint("ClickableViewAccessibility")
  public void bindGestureListener() {
    GestureDetectorCompat gestureDetectorCompat = new GestureDetectorCompat(getContext(), new OnGestureListener());
    setOnTouchListener((v, event) -> gestureDetectorCompat.onTouchEvent(event));
  }

  private void ellipsizeAnyTextForMaxLength() {
    if (maxLength > 0 && getText().length() > maxLength + 1) {
      SpannableStringBuilder newContent = new SpannableStringBuilder();

      newContent.append(getText(maxLength))
                .append(ELLIPSIS)
                .append(Util.emptyIfNull(overflowText));

      EmojiParser.CandidateList newCandidates = isInEditMode() ? null : EmojiProvider.getCandidates(newContent);

      Spannable newTextToSet;
      if (useSystemEmoji || newCandidates == null || newCandidates.size() == 0) {
        newTextToSet = newContent;
      } else {
        newTextToSet = EmojiProvider.emojify(newCandidates, newContent, this, isJumbomoji || forceJumboEmoji);
      }

      super.setText(newTextToSet, BufferType.SPANNABLE);
    }
  }

  private void ellipsizeEmojiTextForMaxLines() {
    Runnable ellipsize = () -> {
      int maxLines = TextViewCompat.getMaxLines(EmojiTextView.this);
      if (maxLines <= 0 && maxLength < 0) {
        return;
      }

      int lineCount = getLineCount();
      if (lineCount > maxLines) {
        int overflowStart = getLayout().getLineStart(maxLines - 1);

        if (maxLength > 0 && overflowStart > maxLength) {
          ellipsizeAnyTextForMaxLength();
          return;
        }

        int          overflowEnd = getLayout().getLineEnd(maxLines);
        CharSequence overflow    = new SpannableString(getText().subSequence(overflowStart, overflowEnd).toString());
        float        adjust      = overflowText != null ? getPaint().measureText(overflowText, 0, overflowText.length()) : 0f;
        CharSequence ellipsized  = TextUtils.ellipsize(overflow, getPaint(), getWidth() - adjust, TextUtils.TruncateAt.END);

        SpannableStringBuilder newContent = new SpannableStringBuilder();
        newContent.append(getText().subSequence(0, overflowStart).toString())
                  .append(ellipsized.subSequence(0, ellipsized.length()).toString());

        if (newContent.length() > 0) {
          TextUtils.copySpansFrom(getText(newContent.length() - 1), 0, newContent.length() - 1, Object.class, newContent, 0);
        }

        newContent.append(Optional.ofNullable(overflowText).orElse(""));

        EmojiParser.CandidateList newCandidates = isInEditMode() ? null : EmojiProvider.getCandidates(newContent);

        if (useSystemEmoji || newCandidates == null || newCandidates.size() == 0) {
          super.setText(newContent, BufferType.SPANNABLE);
        } else {
          CharSequence emojified = EmojiProvider.emojify(newCandidates, newContent, this, isJumbomoji || forceJumboEmoji);
          super.setText(emojified, BufferType.SPANNABLE);
        }
      } else if (maxLength > 0) {
        ellipsizeAnyTextForMaxLength();
      }
    };

    if (getLayout() != null) {
      ellipsize.run();
    } else {
      ViewKt.doOnPreDraw(this, view -> {
        ellipsize.run();
        return Unit.INSTANCE;
      });
    }
  }

  /** Get text but truncated to maxLength, adjusts for end mentions and converts style spans to be exclusive on start and end. */
  private SpannableString getText(int maxLength) {
    SpannableString shortenedText = new SpannableString(getText().subSequence(0, maxLength));
    List<Annotation> mentionAnnotations = MentionAnnotation.getMentionAnnotations(shortenedText, maxLength - 1, maxLength);
    if (!mentionAnnotations.isEmpty()) {
      shortenedText = new SpannableString(shortenedText.subSequence(0, shortenedText.getSpanStart(mentionAnnotations.get(0))));
    }

    Object[] endSpans = shortenedText.getSpans(shortenedText.length() - 1, shortenedText.length(), Object.class);
    for (Object span : endSpans) {
      if (shortenedText.getSpanFlags(span) == Spanned.SPAN_EXCLUSIVE_INCLUSIVE) {
        int start = shortenedText.getSpanStart(span);
        int end   = shortenedText.getSpanEnd(span);
        shortenedText.removeSpan(span);
        shortenedText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }

    return shortenedText;
  }

  private boolean unchanged(CharSequence text, CharSequence overflowText, BufferType bufferType) {
    return Util.equals(previousText, text) &&
           Util.equals(previousOverflowText, overflowText) &&
           Util.equals(previousBufferType, bufferType) &&
           useSystemEmoji == useSystemEmoji() &&
           !sizeChangeInProgress &&
           previousTransformationMethod == getTransformationMethod();
  }

  private boolean useSystemEmoji() {
    return isInEditMode() || SignalStore.settings().isPreferSystemEmoji();
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
    if (drawable instanceof EmojiProvider.EmojiDrawable) invalidate();
    else super.invalidateDrawable(drawable);
  }

  @Override
  public void setTextColor(int color) {
    super.setTextColor(color);
    if (spoilerRendererDelegate != null) {
      spoilerRendererDelegate.updateFromTextColor();
    }
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

  /**
   * Due to some peculiarities in how TextView deals with touch events, it's really easy to accidentally trigger
   * a click (say, when you try to scroll but you're at the bottom of a view.) Because of this, we handle these
   * events manually.
   */
  private class OnGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(@NonNull MotionEvent e) {
      return true;
    }

    @Override
    public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
      if (!canScrollVertically((int) distanceY)) {
        return true;
      }

      int maxScrollDistance = computeVerticalScrollRange() - computeHorizontalScrollExtent();
      scrollTo(0, Util.clamp(getScrollY() + (int) distanceY, 0, maxScrollDistance));
      return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
      return performClick();
    }
  }
}
