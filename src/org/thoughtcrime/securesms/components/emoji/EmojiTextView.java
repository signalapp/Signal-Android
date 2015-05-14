package org.thoughtcrime.securesms.components.emoji;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build.VERSION_CODES;
import android.support.v7.widget.AppCompatTextView;
import android.text.method.TransformationMethod;
import android.util.AttributeSet;
import android.view.View;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.InvalidatingPageLoadedListener;

public class EmojiTextView extends AppCompatTextView {
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

  private void init() {
    setTransformationMethod(new EmojiTransformationMethod());
  }

  private static class EmojiTransformationMethod implements TransformationMethod {

    @Override public CharSequence getTransformation(CharSequence source, View view) {
      return EmojiProvider.getInstance(view.getContext()).emojify(source,
                                                                  EmojiProvider.EMOJI_SMALL,
                                                                  new InvalidatingPageLoadedListener(view));
    }

    @Override public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                                         int direction, Rect previouslyFocusedRect) { }
  }
}
