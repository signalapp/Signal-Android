package org.thoughtcrime.securesms.components.emoji;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.widget.TextView;

public class EmojiTextView extends TextView {
  public EmojiTextView(Context context) {
    super(context);
    init();
  }

  public EmojiTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public EmojiTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  private void init() {
    setTransformationMethod(new EmojiTransformationMethod());
  }
}
