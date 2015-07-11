package org.thoughtcrime.securesms.components.emoji;

import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;

public class EmojiFilter implements InputFilter, OnGlobalLayoutListener {
  private TextView view;

  public EmojiFilter(TextView view) {
    this.view = view;
  }

  @Override public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend)
  {
    char[] v = new char[end - start];
    TextUtils.getChars(source, start, end, v, 0);
    Spannable emojified = EmojiProvider.getInstance(view.getContext()).emojify(new String(v), view);
    if (source instanceof Spanned) {
      TextUtils.copySpansFrom((Spanned) source, start, end, null, emojified, 0);
    }
    view.getViewTreeObserver().addOnGlobalLayoutListener(this);
    if (view.getWidth() == 0 || view.getEllipsize() != TruncateAt.END) {
      return emojified;
    } else {
      return TextUtils.ellipsize(emojified,
                                 view.getPaint(),
                                 view.getWidth() - view.getPaddingRight() - view.getPaddingLeft(),
                                 TruncateAt.END);
    }
  }

  @SuppressWarnings("deprecation")
  @Override public void onGlobalLayout() {
    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
      view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }
    else {
      view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    view.invalidate();
  }
}
