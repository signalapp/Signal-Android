/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
public class KeyboardAwareLinearLayout extends LinearLayout {
  private static final String TAG  = KeyboardAwareLinearLayout.class.getSimpleName();
  private static final Rect   rect = new Rect();

  private KeyboardListener listener;

  public KeyboardAwareLinearLayout(Context context) {
    super(context);
  }

  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public interface KeyboardListener {
    public void onKeyboardShown(int keyboardHeight);
  }

  public void setListener(KeyboardListener listener) {
    this.listener = listener;
  }

  /**
   * inspired by http://stackoverflow.com/a/7104303
   * @param widthMeasureSpec width measure
   * @param heightMeasureSpec height measure
   */
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
    int statusBarHeight = res > 0 ? getResources().getDimensionPixelSize(res) : 0;

    final int availableHeight = this.getRootView().getHeight() - statusBarHeight;
    getWindowVisibleDisplayFrame(rect);

    final int keyboardHeight = availableHeight - (rect.bottom - rect.top);

    if (listener != null) listener.onKeyboardShown(keyboardHeight);

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
