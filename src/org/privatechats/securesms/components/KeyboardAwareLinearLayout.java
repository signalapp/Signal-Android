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
package org.privatechats.securesms.components;

import android.annotation.TargetApi;
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

import org.privatechats.securesms.R;
import org.privatechats.securesms.util.ServiceUtil;
import org.privatechats.securesms.util.Util;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
public class KeyboardAwareLinearLayout extends LinearLayoutCompat {
  private static final String TAG = KeyboardAwareLinearLayout.class.getSimpleName();

  private final Rect                          rect                       = new Rect();
  private final Set<OnKeyboardHiddenListener> hiddenListeners            = new HashSet<>();
  private final Set<OnKeyboardShownListener>  shownListeners             = new HashSet<>();
  private final int                           minKeyboardSize;
  private final int                           minCustomKeyboardSize;
  private final int                           defaultCustomKeyboardSize;
  private final int                           minCustomKeyboardTopMargin;
  private final int                           statusBarHeight;

  private int viewInset;

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
    final int statusBarRes = getResources().getIdentifier("status_bar_height", "dimen", "android");
    minKeyboardSize            = getResources().getDimensionPixelSize(R.dimen.min_keyboard_size);
    minCustomKeyboardSize      = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_size);
    defaultCustomKeyboardSize  = getResources().getDimensionPixelSize(R.dimen.default_custom_keyboard_size);
    minCustomKeyboardTopMargin = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_top_margin);
    statusBarHeight            = statusBarRes > 0 ? getResources().getDimensionPixelSize(statusBarRes) : 0;
    viewInset                  = getViewInset();
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
      Log.w(TAG, "rotation changed");
      onKeyboardClose();
    }
  }

  private void updateKeyboardState() {
    if (isLandscape()) {
      if (keyboardOpen) onKeyboardClose();
      return;
    }

    if (viewInset == 0 && Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) viewInset = getViewInset();
    final int availableHeight = this.getRootView().getHeight() - statusBarHeight - viewInset;
    getWindowVisibleDisplayFrame(rect);

    final int keyboardHeight = availableHeight - (rect.bottom - rect.top);

    if (keyboardHeight > minKeyboardSize) {
      if (getKeyboardHeight() != keyboardHeight) setKeyboardPortraitHeight(keyboardHeight);
      if (!keyboardOpen)                         onKeyboardOpen(keyboardHeight);
    } else if (keyboardOpen) {
      onKeyboardClose();
    }
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private int getViewInset() {
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
    Log.w(TAG, "onKeyboardOpen(" + keyboardHeight + ")");
    keyboardOpen = true;

    notifyShownListeners();
  }

  protected void onKeyboardClose() {
    Log.w(TAG, "onKeyboardClose()");
    keyboardOpen = false;
    notifyHiddenListeners();
  }

  public boolean isKeyboardOpen() {
    return keyboardOpen;
  }

  public int getKeyboardHeight() {
    return isLandscape() ? getKeyboardLandscapeHeight() : getKeyboardPortraitHeight();
  }

  public boolean isLandscape() {
    int rotation = getDeviceRotation();
    return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
  }
  private int getDeviceRotation() {
    return ServiceUtil.getWindowManager(getContext()).getDefaultDisplay().getRotation();
  }

  private int getKeyboardLandscapeHeight() {
    return Math.max(getHeight(), getRootView().getHeight()) / 2;
  }

  private int getKeyboardPortraitHeight() {
    int keyboardHeight = PreferenceManager.getDefaultSharedPreferences(getContext())
                                          .getInt("keyboard_height_portrait", defaultCustomKeyboardSize);
    return Util.clamp(keyboardHeight, minCustomKeyboardSize, getRootView().getHeight() - minCustomKeyboardTopMargin);
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

  public void postOnKeyboardOpen(final Runnable runnable) {
    if (!keyboardOpen) {
      addOnKeyboardShownListener(new OnKeyboardShownListener() {
        @Override public void onKeyboardShown() {
          removeOnKeyboardShownListener(this);
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
    final Set<OnKeyboardHiddenListener> listeners = new HashSet<>(hiddenListeners);
    for (OnKeyboardHiddenListener listener : listeners) {
      listener.onKeyboardHidden();
    }
  }

  private void notifyShownListeners() {
    final Set<OnKeyboardShownListener> listeners = new HashSet<>(shownListeners);
    for (OnKeyboardShownListener listener : listeners) {
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
