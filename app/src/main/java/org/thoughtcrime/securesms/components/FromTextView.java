package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.SimpleEmojiTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

public class FromTextView extends SimpleEmojiTextView {

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(recipient, null);
  }

  public void setText(Recipient recipient, @Nullable CharSequence suffix) {
    setText(recipient, recipient.getDisplayName(getContext()), suffix);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, @Nullable CharSequence suffix) {
    setText(recipient, fromString, suffix, true);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, @Nullable CharSequence suffix, boolean asThread) {
    setText(recipient, fromString, suffix, asThread, false);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, @Nullable CharSequence suffix, boolean asThread, boolean showSelfAsYou) {
    setText(recipient, fromString, suffix, asThread, showSelfAsYou, false);
  }

  public void setText(Recipient recipient, @Nullable CharSequence fromString, @Nullable CharSequence suffix, boolean asThread, boolean showSelfAsYou, boolean isPinned) {
    SpannableStringBuilder builder  = new SpannableStringBuilder();

    if (asThread && recipient.isSelf() && showSelfAsYou) {
      builder.append(getContext().getString(R.string.Recipient_you));
    } else if (asThread && recipient.isSelf()) {
      builder.append(getContext().getString(R.string.note_to_self));
    } else {
      builder.append(fromString);
    }

    if (suffix != null) {
      builder.append(suffix);
    }

    if (asThread && recipient.getShowVerified()) {
      Drawable official = ContextUtil.requireDrawable(getContext(), R.drawable.ic_official_20);
      official.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20));

      builder.append(" ")
             .append(SpanUtil.buildCenteredImageSpan(official));
    }

    if (recipient.isMuted()) {
      builder.append(" ")
             .append(SpanUtil.buildCenteredImageSpan(getMuted()));
    }

    setText(builder);

    if      (recipient.isBlocked())                           setCompoundDrawablesRelativeWithIntrinsicBounds(getBlocked(), null, null, null);
    else if (RemoteConfig.getInlinePinnedChats() && isPinned) setCompoundDrawablesRelativeWithIntrinsicBounds(getPinned(), null, null, null);
    else                                                      setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
  }

  private Drawable getBlocked() {
    return getDrawable(R.drawable.symbol_block_16, R.color.signal_icon_tint_secondary);
  }

  private Drawable getMuted() {
    return getDrawable(R.drawable.ic_bell_disabled_16, R.color.signal_icon_tint_secondary);
  }

  private Drawable getPinned() {
    return getDrawable(R.drawable.symbol_pin_16, R.color.signal_colorOnSurface);
  }

  private Drawable getDrawable(@DrawableRes int drawable, int colorRes) {
    Drawable mutedDrawable = ContextUtil.requireDrawable(getContext(), drawable);
    mutedDrawable.setBounds(0, 0, ViewUtil.dpToPx(16), ViewUtil.dpToPx(16));
    DrawableUtil.tint(mutedDrawable, ContextCompat.getColor(getContext(), colorRes));
    return mutedDrawable;
  }
}
