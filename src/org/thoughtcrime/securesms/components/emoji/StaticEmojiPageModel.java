package org.thoughtcrime.securesms.components.emoji;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

public class StaticEmojiPageModel extends EmojiPageModel {
  @DrawableRes private final int   icon;
  @NonNull     private final int[] codePoints;

  public StaticEmojiPageModel(@DrawableRes int icon, @NonNull int[] codePoints) {
    this.icon       = icon;
    this.codePoints = codePoints;
  }

  public int getIconRes() {
    return icon;
  }

  @NonNull public int[] getCodePoints() {
    return codePoints;
  }
}
