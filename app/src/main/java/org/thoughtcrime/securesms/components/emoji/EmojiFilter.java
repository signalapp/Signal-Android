package org.thoughtcrime.securesms.components.emoji;

import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.TextView;

public class EmojiFilter implements InputFilter {
  private TextView view;
  private boolean  jumboEmoji;

  public EmojiFilter(TextView view, boolean jumboEmoji) {
    this.view       = view;
    this.jumboEmoji = jumboEmoji;
  }

  @Override
  public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend)
  {
    char[] v = new char[end - start];
    TextUtils.getChars(source, start, end, v, 0);

    Spannable emojified = EmojiProvider.emojify(new String(v), view, jumboEmoji);

    if (source instanceof Spanned && emojified != null) {
      TextUtils.copySpansFrom((Spanned) source, start, end, null, emojified, 0);
    }

    return emojified;
  }
}
