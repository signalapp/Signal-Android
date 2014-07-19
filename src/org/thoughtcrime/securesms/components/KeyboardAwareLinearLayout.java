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
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
public class KeyboardAwareLinearLayout extends LinearLayout {
  private static final String TAG  = KeyboardAwareLinearLayout.class.getSimpleName();
  private static final Rect   rect = new Rect();

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

    onKeyboardShown(keyboardHeight);

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void onKeyboardShown(int keyboardHeight) {
    Log.w(TAG, "keyboard shown, height " + keyboardHeight);
    if (keyboardHeight > getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height)) {
      WindowManager wm = (WindowManager) getContext().getSystemService(Activity.WINDOW_SERVICE);
      if (wm == null || wm.getDefaultDisplay() == null) {
        return;
      }
      int rotation = wm.getDefaultDisplay().getRotation();

      switch (rotation) {
        case Surface.ROTATION_270:
        case Surface.ROTATION_90:
          setKeyboardLandscapeHeight(keyboardHeight);
          break;
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
          setKeyboardPortraitHeight(keyboardHeight);
      }
    }
  }

  public int getKeyboardHeight() {
    WindowManager      wm    = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
    if (wm == null || wm.getDefaultDisplay() == null) {
      throw new AssertionError("WindowManager was null or there is no default display");
    }

    int rotation = wm.getDefaultDisplay().getRotation();

    switch (rotation) {
      case Surface.ROTATION_270:
      case Surface.ROTATION_90:
        return getKeyboardLandscapeHeight();
      case Surface.ROTATION_0:
      case Surface.ROTATION_180:
      default:
        return getKeyboardPortraitHeight();
    }
  }

  private int getKeyboardLandscapeHeight() {
    return PreferenceManager.getDefaultSharedPreferences(getContext())
                            .getInt("keyboard_height_landscape",
                                    getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
  }

  private int getKeyboardPortraitHeight() {
    return PreferenceManager.getDefaultSharedPreferences(getContext())
                            .getInt("keyboard_height_portrait",
                                    getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
  }

  private void setKeyboardLandscapeHeight(int height) {
    PreferenceManager.getDefaultSharedPreferences(getContext())
                     .edit().putInt("keyboard_height_landscape", height).apply();
  }

  private void setKeyboardPortraitHeight(int height) {
    PreferenceManager.getDefaultSharedPreferences(getContext())
                     .edit().putInt("keyboard_height_portrait", height).apply();
  }

}
