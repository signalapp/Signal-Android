package org.thoughtcrime.securesms.components.emoji;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;

public class EmojiToggle extends ImageButton {

  private Drawable emojiToggle;
  private Drawable imeToggle;
  private OnClickListener listener;

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

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
  }

  public void toggle() {
    if (getDrawable() == emojiToggle) {
      setImageDrawable(imeToggle);
    } else {
      setImageDrawable(emojiToggle);
    }
  }

  private void initialize() {
    initializeResources();
    initializeListeners();
  }

  private void initializeResources() {
    int attributes[] = new int[] {R.attr.conversation_emoji_toggle,
                                  R.attr.conversation_keyboard_toggle};

    TypedArray drawables = getContext().obtainStyledAttributes(attributes);
    this.emojiToggle     = drawables.getDrawable(0);
    this.imeToggle       = drawables.getDrawable(1);

    drawables.recycle();

    setImageDrawable(this.emojiToggle);
  }

  private void initializeListeners() {
    super.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        toggle();

        if (listener != null)
          listener.onClick(v);
      }
    });
  }
}
