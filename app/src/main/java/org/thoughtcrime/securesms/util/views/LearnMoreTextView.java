package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.ThemeUtil;

/**
 * Appends an optional "Learn more" link to a given piece of text.
 */
public class LearnMoreTextView extends AppCompatTextView {

  private OnClickListener linkListener;
  private Spannable       link;
  private boolean         visible;
  private CharSequence    baseText;
  private int             linkColor;

  public LearnMoreTextView(Context context) {
    super(context);
    init();
  }

  public LearnMoreTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    setMovementMethod(LinkMovementMethod.getInstance());
    setLinkTextInternal(R.string.LearnMoreTextView_learn_more);
    setLinkColor(ContextCompat.getColor(getContext(), R.color.signal_colorOnSurface));
    visible = true;
  }

  @Override
  public void setText(CharSequence text, BufferType type) {
    baseText = text;
    setTextInternal(baseText, type);
  }

  @Override
  public void setTextColor(int color) {
    super.setTextColor(color);
  }

  public void setOnLinkClickListener(@Nullable OnClickListener listener) {
    this.linkListener = listener;
  }

  public void setLearnMoreVisible(boolean visible) {
    this.visible = visible;
    setTextInternal(baseText, visible ? BufferType.SPANNABLE : BufferType.NORMAL);
  }

  public void setLearnMoreVisible(boolean visible, @StringRes int linkText) {
    setLinkTextInternal(linkText);
    this.visible = visible;
    setTextInternal(baseText, visible ? BufferType.SPANNABLE : BufferType.NORMAL);
  }

  public void setLink(@NonNull String url) {
    setOnLinkClickListener(new OpenUrlOnClickListener(url));
  }

  public void setLinkColor(@ColorInt int linkColor) {
    this.linkColor = linkColor;
  }

  private void setLinkTextInternal(@StringRes int linkText) {
    ClickableSpan clickable = new ClickableSpan() {
      @Override
      public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(false);
        ds.setColor(linkColor);
      }

      @Override
      public void onClick(@NonNull View widget) {
        if (linkListener != null) {
          linkListener.onClick(widget);
        }
      }
    };

    link = new SpannableString(getContext().getString(linkText));
    link.setSpan(clickable, 0, link.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  private void setTextInternal(CharSequence text, BufferType type) {
    if (visible) {
      SpannableStringBuilder builder = new SpannableStringBuilder();
      builder.append(text).append(' ').append(link);

      super.setText(builder, BufferType.SPANNABLE);
    } else {
      super.setText(text, type);
    }
  }

  private static class OpenUrlOnClickListener implements OnClickListener {

    private final String url;

    public OpenUrlOnClickListener(@NonNull String url) {
      this.url = url;
    }

    @Override
    public void onClick(View v) {
      CommunicationActions.openBrowserLink(v.getContext(), url);
    }
  }
}

