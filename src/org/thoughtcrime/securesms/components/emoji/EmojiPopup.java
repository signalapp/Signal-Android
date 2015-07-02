package org.thoughtcrime.securesms.components.emoji;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiEventListener;

public class EmojiPopup extends PopupWindow {
  private View parent;

  public EmojiPopup(View parent) {
    super(new EmojiDrawer(parent.getContext()),
          parent.getWidth(),
          parent.getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
    this.parent = parent;
  }

  public void setEmojiEventListener(EmojiEventListener listener) {
    ((EmojiDrawer)getContentView()).setEmojiEventListener(listener);
  }

  public void show(int height) {
    setHeight(height);
    showAtLocation(parent, Gravity.BOTTOM | Gravity.LEFT, 0, 0);
  }
}
