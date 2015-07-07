package org.thoughtcrime.securesms.components.emoji;

import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.PopupWindow;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiEventListener;

public class EmojiPopup extends PopupWindow {
  private static final String TAG = EmojiPopup.class.getSimpleName();
  private KeyboardAwareLinearLayout parent;

  public EmojiPopup(KeyboardAwareLinearLayout parent) {
    super(new EmojiDrawer(parent.getContext()),
          parent.getWidth(),
          parent.getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
    this.parent = parent;
    Log.w("EmojiPopup", "popup initialized with width " + parent.getWidth());
  }

  public void setEmojiEventListener(EmojiEventListener listener) {
    ((EmojiDrawer)getContentView()).setEmojiEventListener(listener);
  }

  public void show() {
    setHeight(parent.getKeyboardHeight());
    setWidth(parent.getWidth());
    parent.padForCustomKeyboard(getHeight());
    Log.w(TAG, String.format("show(%d, %d)", getWidth(), getHeight()));
    showAtLocation(parent, Gravity.BOTTOM | Gravity.LEFT, 0, 0);
  }

  @Override
  public void dismiss() {
    super.dismiss();
  }

  public void update() {
    update(parent, 0, 0, parent.getWidth(), -1);
  }
}
