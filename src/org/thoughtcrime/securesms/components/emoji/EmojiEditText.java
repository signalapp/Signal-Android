package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;

public class EmojiEditText extends AppCompatEditText {
  public EmojiEditText(Context context) {
    super(context);
    init();
  }

  public EmojiEditText(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiEditText(Context context, AttributeSet attrs,
                       int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setTransformationMethod(new EmojiTransformationMethod());
  }

  public void insertEmoji(int codePoint) {
    final char[] chars = Character.toChars(codePoint);
    final String text  = new String(chars);
    final int    start = getSelectionStart();
    final int    end   = getSelectionEnd();

    getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
    setSelection(end + chars.length);
  }
}
