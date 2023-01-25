/**
 * Copyright (C) 2014 Open Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;

import androidx.appcompat.widget.LinearLayoutCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 */
public class KeyboardAwareLinearLayout extends LinearLayoutCompat {
  private static final String TAG = Log.tag(KeyboardAwareLinearLayout.class);

  private final Rect                          rect            = new Rect();
  private final Set<OnKeyboardHiddenListener> hiddenListeners = new HashSet<>();
  private final Set<OnKeyboardShownListener>  shownListeners  = new HashSet<>();
  private final DisplayMetrics                displayMetrics  = new DisplayMetrics();

  private final int minKeyboardSize;
  private final int minCustomKeyboardSize;
  private final int defaultCustomKeyboardSize;
  private final int minCustomKeyboardTopMarginPortrait;
  private final int minCustomKeyboardTopMarginLandscape;
  private final int minCustomKeyboardTopMarginLandscapeBubble;
  private final int statusBarHeight;

  private int viewInset;

  private boolean keyboardOpen = false;
  private int     rotation     = 0;
  private boolean isBubble     = false;

  public KeyboardAwareLinearLayout(Context context) {
    this(context, null);
  }

  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public KeyboardAwareLinearLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    minKeyboardSize                           = getResources().getDimensionPixelSize(R.dimen.min_keyboard_size);
    minCustomKeyboardSize                     = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_size);
    defaultCustomKeyboardSize                 = getResources().getDimensionPixelSize(R.dimen.default_custom_keyboard_size);
    minCustomKeyboardTopMarginPortrait        = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_top_margin_portrait);
    minCustomKeyboardTopMarginLandscape       = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_top_margin_portrait);
    minCustomKeyboardTopMarginLandscapeBubble = getResources().getDimensionPixelSize(R.dimen.min_custom_keyboard_top_margin_landscape_bubble);
    statusBarHeight                           = ViewUtil.getStatusBarHeight(this);
    viewInset                                 = getViewInset();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    updateRotation();
    updateKeyboardState();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public void setIsBubble(boolean isBubble) {
    this.isBubble = isBubble;
  }

  private void updateRotation() {
    int oldRotation = rotation;
    rotation = getDeviceRotation();
    if (oldRotation != rotation) {
      Log.i(TAG, "rotation changed");
      onKeyboardClose();
    }
  }

  private void updateKeyboardState() {
    if (viewInset == 0) viewInset = getViewInset();

    getWindowVisibleDisplayFrame(rect);

    final int availableHeight = getAvailableHeight();
    final int keyboardHeight  = availableHeight - rect.bottom;

    if (keyboardHeight > minKeyboardSize) {
      if (getKeyboardHeight() != keyboardHeight) {
        if (isLandscape()) {
          setKeyboardLandscapeHeight(keyboardHeight);
        } else {
          setKeyboardPortraitHeight(keyboardHeight);
        }
      }
      if (!keyboardOpen) {
        onKeyboardOpen(keyboardHeight);
      }
    } else if (keyboardOpen) {
      onKeyboardClose();
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    rotation = getDeviceRotation();
    if (Build.VERSION.SDK_INT >= 23 && getRootWindowInsets() != null) {
      int          bottomInset;
      WindowInsets windowInsets = getRootWindowInsets();

      if (Build.VERSION.SDK_INT >= 30) {
        bottomInset = windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom;
      } else {
        bottomInset = windowInsets.getStableInsetBottom();
      }

      if (bottomInset != 0 && (viewInset == 0 || viewInset == statusBarHeight)) {
        Log.i(TAG, "Updating view inset based on WindowInsets. viewInset: " + viewInset + " windowInset: " + bottomInset);
        viewInset = bottomInset;
      }
    }
  }

  private int getViewInset() {
    try {
      Field attachInfoField = View.class.getDeclaredField("mAttachInfo");
      attachInfoField.setAccessible(true);
      Object attachInfo = attachInfoField.get(this);
      if (attachInfo != null) {
        Field stableInsetsField = attachInfo.getClass().getDeclaredField("mStableInsets");
        stableInsetsField.setAccessible(true);
        Rect insets = (Rect) stableInsetsField.get(attachInfo);
        if (insets != null) {
          return insets.bottom;
        }
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Do nothing
    }
    return statusBarHeight;
  }

  private int getAvailableHeight() {
    final int availableHeight = this.getRootView().getHeight() - viewInset;
    final int availableWidth  = this.getRootView().getWidth();

    if (isLandscape() && availableHeight > availableWidth) {
      //noinspection SuspiciousNameCombination
      return availableWidth;
    }

    return availableHeight;
  }

  protected void onKeyboardOpen(int keyboardHeight) {
    Log.i(TAG, "onKeyboardOpen(" + keyboardHeight + ")");
    keyboardOpen = true;

    notifyShownListeners();
  }

  protected void onKeyboardClose() {
    Log.i(TAG, "onKeyboardClose()");
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
    if (Build.VERSION.SDK_INT >= 30) {
      getContext().getDisplay().getRealMetrics(displayMetrics);
    } else {
      ServiceUtil.getWindowManager(getContext()).getDefaultDisplay().getRealMetrics(displayMetrics);
    }
    return displayMetrics.widthPixels > displayMetrics.heightPixels ? Surface.ROTATION_90 : Surface.ROTATION_0;
  }

  private int getKeyboardLandscapeHeight() {
    if (isBubble) {
      return getRootView().getHeight() - minCustomKeyboardTopMarginLandscapeBubble;
    }

    int keyboardHeight = PreferenceManager.getDefaultSharedPreferences(getContext())
                                          .getInt("keyboard_height_landscape", defaultCustomKeyboardSize);
    return Util.clamp(keyboardHeight, minCustomKeyboardSize, getRootView().getHeight() - minCustomKeyboardTopMarginLandscape);
  }

  private int getKeyboardPortraitHeight() {
    if (isBubble) {
      int height = getRootView().getHeight();
      return height - (int)(height * 0.45);
    }

    int keyboardHeight = PreferenceManager.getDefaultSharedPreferences(getContext())
                                          .getInt("keyboard_height_portrait", defaultCustomKeyboardSize);
    return Util.clamp(keyboardHeight, minCustomKeyboardSize, getRootView().getHeight() - minCustomKeyboardTopMarginPortrait);
  }

  private void setKeyboardPortraitHeight(int height) {
    if (isBubble) {
      return;
    }

    PreferenceManager.getDefaultSharedPreferences(getContext())
                     .edit().putInt("keyboard_height_portrait", height).apply();
  }

  private void setKeyboardLandscapeHeight(int height) {
    if (isBubble) {
      return;
    }

    PreferenceManager.getDefaultSharedPreferences(getContext())
                     .edit().putInt("keyboard_height_landscape", height).apply();
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
