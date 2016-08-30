package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputFilter;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider.EmojiDrawable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class EmojiEditText extends AppCompatEditText {
  private static final String TAG = EmojiEditText.class.getSimpleName();

  public EmojiEditText(Context context) {
    this(context, null);
  }

  public EmojiEditText(Context context, AttributeSet attrs) {
    this(context, attrs, R.attr.editTextStyle);
  }

  public EmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    if (!TextSecurePreferences.isSystemEmojiPreferred(getContext())) {
      setFilters(appendEmojiFilter(this.getFilters()));
    }
  }

  public void insertEmoji(String emoji) {
    final int          start = getSelectionStart();
    final int          end   = getSelectionEnd();

    getText().replace(Math.min(start, end), Math.max(start, end), emoji);
    setSelection(start + emoji.length());
  }

  @Override public void invalidateDrawable(@NonNull Drawable drawable) {
    if (drawable instanceof EmojiDrawable) invalidate();
    else                                   super.invalidateDrawable(drawable);
  }

  private InputFilter[] appendEmojiFilter(InputFilter[] originalFilters) {
    int originalLength = originalFilters.length;
    final InputFilter[] result = new InputFilter[originalLength+1];

    result[0] = new EmojiFilter(this);
    System.arraycopy(originalFilters,0,result,1,originalLength);

    return result;
  }
}
