package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.SimpleEmojiTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public class FromTextView extends SimpleEmojiTextView {

  private static final String TAG = Log.tag(FromTextView.class);

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(recipient, true);
  }

  public void setText(Recipient recipient, boolean read) {
    setText(recipient, read, null);
  }

  public void setText(Recipient recipient, boolean read, @Nullable String suffix) {
    setText(recipient, recipient.getDisplayNameOrUsername(getContext()), read, suffix);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, boolean read, @Nullable String suffix) {
    setText(recipient, fromString, read, suffix, true);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, boolean read, @Nullable String suffix, boolean asThread) {
    SpannableStringBuilder builder  = new SpannableStringBuilder();

    if (asThread && recipient.isSelf()) {
      builder.append(getContext().getString(R.string.note_to_self));
    } else {
      builder.append(fromString);
    }

    if (suffix != null) {
      builder.append(suffix);
    }

    if (asThread && recipient.showVerified()) {
      Drawable official = ContextUtil.requireDrawable(getContext(), R.drawable.ic_official_20);
      official.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20));

      builder.append(" ")
             .append(SpanUtil.buildCenteredImageSpan(official));
    }

    setText(builder);

    if      (recipient.isBlocked()) setCompoundDrawablesRelativeWithIntrinsicBounds(getBlocked(), null, null, null);
    else if (recipient.isMuted())   setCompoundDrawablesRelativeWithIntrinsicBounds(getMuted(), null, null, null);
    else                            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
  }

  private Drawable getBlocked() {
    return getDrawable(R.drawable.symbol_block_16);
  }

  private Drawable getMuted() {
    return getDrawable(R.drawable.ic_bell_disabled_16);
  }

  private Drawable getDrawable(@DrawableRes int drawable) {
    Drawable mutedDrawable = ContextUtil.requireDrawable(getContext(), drawable);
    mutedDrawable.setBounds(0, 0, ViewUtil.dpToPx(18), ViewUtil.dpToPx(18));
    DrawableUtil.tint(mutedDrawable, ContextCompat.getColor(getContext(), R.color.signal_icon_tint_secondary));
    return mutedDrawable;
  }
}
