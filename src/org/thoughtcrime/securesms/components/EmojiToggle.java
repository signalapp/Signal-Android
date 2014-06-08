/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.components;


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
