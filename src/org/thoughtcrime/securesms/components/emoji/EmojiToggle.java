package org.thoughtcrime.securesms.components.emoji;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.InputManager;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer.EmojiDrawerListener;

public class EmojiToggle extends ImageButton implements OnClickListener, EmojiDrawerListener {

  private Drawable     emojiToggle;
  private Drawable     imeToggle;
  private EmojiDrawer  drawer;
  private InputManager inputManager;

  public EmojiToggle(Context context) {
    super(context);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setToEmoji() {
    setImageDrawable(emojiToggle);
  }

  public void setToIme() {
    setImageDrawable(imeToggle);
  }

  private void initialize() {
    int attributes[] = new int[] {R.attr.conversation_emoji_toggle,
        R.attr.conversation_keyboard_toggle};

    TypedArray drawables = getContext().obtainStyledAttributes(attributes);
    this.emojiToggle     = drawables.getDrawable(0);
    this.imeToggle       = drawables.getDrawable(1);

    drawables.recycle();
    setToEmoji();
    setOnClickListener(this);
  }

  public void attach(InputManager manager, EmojiDrawer drawer) {
    this.inputManager = manager;
    this.drawer       = drawer;
    drawer.setDrawerListener(this);
  }

  @Override public void onClick(View v) {
    if (inputManager == null || drawer == null) return;

    if (inputManager.getCurrentInput() == drawer) {
      inputManager.showSoftkey();
    } else {
      inputManager.show(drawer);
    }
  }

  @Override public void onShown() {
    setToIme();
  }

  @Override public void onHidden() {
    setToEmoji();
  }
}
