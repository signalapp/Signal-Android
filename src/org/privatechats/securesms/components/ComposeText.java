package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ComposeText extends EmojiEditText {
  private SpannableString hint;

  public ComposeText(Context context) {
    super(context);
  }

  public ComposeText(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ComposeText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (!TextUtils.isEmpty(hint)) {
      setHint(ellipsizeToWidth(hint));
    }
  }

  private CharSequence ellipsizeToWidth(CharSequence text) {
    return TextUtils.ellipsize(text,
                               getPaint(),
                               getWidth() - getPaddingLeft() - getPaddingRight(),
                               TruncateAt.END);
  }

  public void setHint(@NonNull String hint) {
    this.hint = new SpannableString(hint);
    this.hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    super.setHint(ellipsizeToWidth(this.hint));
  }

  public void appendInvite(String invite) {
    if (!TextUtils.isEmpty(getText()) && !getText().toString().equals(" ")) {
      append(" ");
    }

    append(invite);
    setSelection(getText().length());
  }

  private boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public void setTransport(TransportOption transport) {
    final boolean enterSends     = TextSecurePreferences.isEnterSendsEnabled(getContext());
    final boolean useSystemEmoji = TextSecurePreferences.isSystemEmojiPreferred(getContext());

    int imeOptions = (getImeOptions() & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_SEND;
    int inputType  = getInputType();

    if (isLandscape()) setImeActionLabel(transport.getComposeHint(), EditorInfo.IME_ACTION_SEND);
    else               setImeActionLabel(null, 0);

    if (useSystemEmoji) {
      inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;
    }

    inputType  = !isLandscape() && enterSends
               ? inputType & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE
               : inputType | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    imeOptions = enterSends
               ? imeOptions & ~EditorInfo.IME_FLAG_NO_ENTER_ACTION
               : imeOptions | EditorInfo.IME_FLAG_NO_ENTER_ACTION;

    setInputType(inputType);
    setImeOptions(imeOptions);
    setHint(transport.getComposeHint());
  }
}
