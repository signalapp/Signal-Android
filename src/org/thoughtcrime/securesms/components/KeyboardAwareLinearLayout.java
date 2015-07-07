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

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
public class KeyboardAwareLinearLayout extends LinearLayoutCompat {
  private static final String TAG = KeyboardAwareLinearLayout.class.getSimpleName();

  private final Rect                          oldRect         = new Rect();
  private final Rect                          newRect         = new Rect();
  private final Set<OnKeyboardHiddenListener> hiddenListeners = new HashSet<>();
  private final Set<OnKeyboardShownListener>  shownListeners  = new HashSet<>();
  private final int minKeyboardSize;

  private boolean keyboardOpen = false;
  private int     rotation     = -1;

  public KeyboardAwareLinearLayout(Context context) {
    this(context, null);
  }

  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    minKeyboardSize = getResources().getDimensionPixelSize(R.dimen.min_keyboard_size);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    updateRotation();
    updateKeyboardState();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  private void updateRotation() {
    int oldRotation = rotation;
    rotation = getDeviceRotation();
    if (oldRotation != rotation) {
      onKeyboardClose();
      oldRect.setEmpty();
    }
  }

  private void updateKeyboardState() {
    int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
    int statusBarHeight = res > 0 ? getResources().getDimensionPixelSize(res) : 0;

    final int availableHeight = this.getRootView().getHeight() - statusBarHeight - getViewInset();
    getWindowVisibleDisplayFrame(newRect);

    final int oldKeyboardHeight = availableHeight - (oldRect.bottom - oldRect.top);
    final int keyboardHeight = availableHeight - (newRect.bottom - newRect.top);

    if (keyboardHeight - oldKeyboardHeight > minKeyboardSize && !keyboardOpen) {
      onKeyboardOpen(keyboardHeight);
    } else if (oldKeyboardHeight - keyboardHeight > minKeyboardSize && keyboardOpen) {
      onKeyboardClose();
    }

    oldRect.set(newRect);
  }

  public void padForCustomKeyboard(final int height) {
    setPadding(0, 0, 0, height);
  }

  public void unpadForCustomKeyboard() {
    setPadding(0, 0, 0, 0);
  }

  private int getViewInset() {
    if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      return 0;
    }

    try {
      Field attachInfoField = View.class.getDeclaredField("mAttachInfo");
      attachInfoField.setAccessible(true);
      Object attachInfo = attachInfoField.get(this);
      if (attachInfo != null) {
        Field stableInsetsField = attachInfo.getClass().getDeclaredField("mStableInsets");
        stableInsetsField.setAccessible(true);
        Rect insets = (Rect)stableInsetsField.get(attachInfo);
        return insets.bottom;
      }
    } catch (NoSuchFieldException nsfe) {
      Log.w(TAG, "field reflection error when measuring view inset", nsfe);
    } catch (IllegalAccessException iae) {
      Log.w(TAG, "access reflection error when measuring view inset", iae);
    }
    return 0;
  }

  protected void onKeyboardOpen(int keyboardHeight) {
    keyboardOpen = true;

    switch (getDeviceRotation()) {
      case Surface.ROTATION_0:
      case Surface.ROTATION_180:
        setKeyboardPortraitHeight(keyboardHeight);
    }
    notifyShownListeners();
    unpadForCustomKeyboard();
  }

  protected void onKeyboardClose() {
    keyboardOpen = false;
    notifyHiddenListeners();
  }

  public boolean isKeyboardOpen() {
    return keyboardOpen;
  }

  public int getKeyboardHeight() {
    switch (getDeviceRotation()) {
      case Surface.ROTATION_270:
      case Surface.ROTATION_90:
        return getKeyboardLandscapeHeight();
      case Surface.ROTATION_0:
      case Surface.ROTATION_180:
      default:
        return getKeyboardPortraitHeight();
    }
  }

  private int getDeviceRotation() {
    return ServiceUtil.getWindowManager(getContext()).getDefaultDisplay().getRotation();
  }

  private int getKeyboardLandscapeHeight() {
    return Math.max(getHeight(), getRootView().getHeight()) / 2;
  }

  private int getKeyboardPortraitHeight() {
    int keyboardHeight = PreferenceManager.getDefaultSharedPreferences(getContext())
                                          .getInt("keyboard_height_portrait",
                                                  getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
    return Math.min(keyboardHeight, getRootView().getHeight() - getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_top_margin));
  }

  private void setKeyboardPortraitHeight(int height) {
    PreferenceManager.getDefaultSharedPreferences(getContext())
                     .edit().putInt("keyboard_height_portrait", height).apply();
  }

  public void postOnKeyboardClose(final Runnable runnable) {
    if (keyboardOpen) {
      addOnKeyboardHiddenListener(new OnKeyboardHiddenListener() {
        @Override public void onKeyboardHidden() {
          removeOnKeyboardHiddenListener(this);
          runnable.run();
        }
      });
    } else {
      runnable.run();
    }
  }

  public void addOnKeyboardHiddenListener(OnKeyboardHiddenListener listener) {
    hiddenListeners.add(listener);
  }

  public void removeOnKeyboardHiddenListener(OnKeyboardHiddenListener listener) {
    hiddenListeners.remove(listener);
  }

  public void addOnKeyboardShownListener(OnKeyboardShownListener listener) {
    shownListeners.add(listener);
  }

  public void removeOnKeyboardShownListener(OnKeyboardShownListener listener) {
    shownListeners.remove(listener);
  }

  private void notifyHiddenListeners() {
    for (OnKeyboardHiddenListener listener : hiddenListeners) {
      listener.onKeyboardHidden();
    }
  }

  private void notifyShownListeners() {
    for (OnKeyboardShownListener listener : shownListeners) {
      listener.onKeyboardShown();
    }
  }

  public interface OnKeyboardHiddenListener {
    void onKeyboardHidden();
  }

  public interface OnKeyboardShownListener {
    void onKeyboardShown();
  }
}
