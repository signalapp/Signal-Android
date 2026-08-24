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
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.signal.core.util.Util;

import java.util.HashSet;
import java.util.Set;

/**
 * LinearLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be. Driven by {@link WindowInsetsCompat.Type#ime()}
 * insets, so it requires an edge-to-edge host window (all activity and dialog windows in the app
 * are edge-to-edge). The reported height excludes the navigation bar, matching what a custom
 * keyboard sitting above a navigation-bar-padded container should be sized to.
 */
public class KeyboardAwareLinearLayout extends LinearLayoutCompat {
  private static final String TAG = Log.tag(KeyboardAwareLinearLayout.class);

  private static final long KEYBOARD_DEBOUNCE = 150;

  private final Set<OnKeyboardHiddenListener> hiddenListeners = new HashSet<>();
  private final Set<OnKeyboardShownListener>  shownListeners  = new HashSet<>();

  private final int minKeyboardSize;
  private final int minCustomKeyboardSize;
  private final int defaultCustomKeyboardSize;
  private final int minCustomKeyboardTopMarginPortrait;
  private final int minCustomKeyboardTopMarginLandscape;
  private final int minCustomKeyboardTopMarginLandscapeBubble;

  private boolean keyboardOpen = false;
  private boolean isBubble     = false;
  private long    openedAt     = 0;
  private int     lastKeyboardHeight;
  private boolean lastKeyboardVisible;

  private InsetPaddingMode insetPaddingMode = InsetPaddingMode.NONE;

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

    ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
      Insets ime  = insets.getInsets(WindowInsetsCompat.Type.ime());
      Insets nav  = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

      lastKeyboardHeight  = Math.max(0, ime.bottom - nav.bottom);
      lastKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

      switch (insetPaddingMode) {
        case NONE:
          break;
        case KEYBOARD:
          setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), lastKeyboardVisible ? lastKeyboardHeight : 0);
          break;
        case KEYBOARD_AND_NAVIGATION_BAR:
          setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), Math.max(nav.bottom, ime.bottom));
          break;
        case SAFE_AREA:
          setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          break;
      }

      updateKeyboardState();

      return insets;
    });
  }

  public void setIsBubble(boolean isBubble) {
    this.isBubble = isBubble;
  }

  /**
   * Opt-in self-padding, replacing the window resize behavior these layouts relied on before the
   * host windows went edge-to-edge. [InsetPaddingMode.NONE] keeps the layout as a pure keyboard
   * event source (e.g. when a parent container already manages insets).
   */
  public void setInsetPaddingMode(@NonNull InsetPaddingMode mode) {
    this.insetPaddingMode = mode;
    ViewCompat.requestApplyInsets(this);
  }

  public enum InsetPaddingMode {
    /** Apply no padding; the layout only reports keyboard events. */
    NONE,
    /** Pad the bottom by the keyboard height above the navigation bar (use inside a container that already clears the navigation bar). */
    KEYBOARD,
    /** Pad the bottom by the keyboard or navigation bar, whichever is larger. */
    KEYBOARD_AND_NAVIGATION_BAR,
    /** Pad all sides by the system bars, and the bottom by the keyboard when it is taller. */
    SAFE_AREA
  }

  private void updateKeyboardState() {
    if (lastKeyboardVisible && lastKeyboardHeight > minKeyboardSize) {
      if (getKeyboardHeight() != lastKeyboardHeight) {
        if (isLandscape()) {
          setKeyboardLandscapeHeight(lastKeyboardHeight);
        } else {
          setKeyboardPortraitHeight(lastKeyboardHeight);
        }
      }
      if (!keyboardOpen) {
        onKeyboardOpen(lastKeyboardHeight);
      }
    } else if (keyboardOpen) {
      onKeyboardClose();
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    ViewCompat.requestApplyInsets(this);
  }

  protected void onKeyboardOpen(int keyboardHeight) {
    Log.i(TAG, "onKeyboardOpen(" + keyboardHeight + ")");
    keyboardOpen = true;
    openedAt = System.currentTimeMillis();

    notifyShownListeners();
  }

  protected void onKeyboardClose() {
    if (System.currentTimeMillis() - openedAt < KEYBOARD_DEBOUNCE) {
      Log.i(TAG, "Delaying onKeyboardClose()");
      postDelayed(this::updateKeyboardState, KEYBOARD_DEBOUNCE);
      return;
    }

    Log.i(TAG, "onKeyboardClose()");
    keyboardOpen = false;
    openedAt = 0;
    notifyHiddenListeners();
  }

  public boolean isKeyboardOpen() {
    return keyboardOpen;
  }

  public int getKeyboardHeight() {
    return isLandscape() ? getKeyboardLandscapeHeight() : getKeyboardPortraitHeight();
  }

  public boolean isLandscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
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
