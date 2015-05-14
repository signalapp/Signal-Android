package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.util.Log;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.InvalidatingPageLoadedListener;

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
  }

  public void insertEmoji(int codePoint) {
    final int          start = getSelectionStart();
    final int          end   = getSelectionEnd();
    final char[]       chars = Character.toChars(codePoint);
    final CharSequence text  = EmojiProvider.getInstance(getContext()).emojify(new String(chars),
                                                                               EmojiProvider.EMOJI_SMALL,
                                                                               new InvalidatingPageLoadedListener(this));

    getText().replace(Math.min(start, end), Math.max(start, end), text);
    setSelection(end + chars.length);
  }
}
