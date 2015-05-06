package org.thoughtcrime.securesms.components.emoji;

import android.graphics.Rect;
import android.text.method.TransformationMethod;
import android.view.View;

import org.thoughtcrime.securesms.components.emoji.EmojiProvider.InvalidatingPageLoadedListener;

class EmojiTransformationMethod implements TransformationMethod {

  @Override public CharSequence getTransformation(CharSequence source, View view) {
    return EmojiProvider.getInstance(view.getContext()).emojify(source, EmojiProvider.EMOJI_SMALL, new InvalidatingPageLoadedListener(view));
  }

  @Override public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                                       int direction, Rect previouslyFocusedRect) { }
}
