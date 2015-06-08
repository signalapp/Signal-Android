package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.thoughtcrime.securesms.R;

public class SoftKeyboardUtil {
  private static final String TAG  = SoftKeyboardUtil.class.getSimpleName();
  private static final Rect   rect = new Rect();

  public static int onMeasure(ViewGroup view) {
    view.getWindowVisibleDisplayFrame(rect);
    final int res               = view.getResources().getIdentifier("status_bar_height", "dimen", "android");
    final int statusBarHeight   = res > 0 ? view.getResources().getDimensionPixelSize(res) : 0;
    final int availableHeight   = view.getRootView().getHeight() - statusBarHeight;
    final int keyboardHeight    = availableHeight                - (rect.bottom - rect.top);
    final int minKeyboardHeight = view.getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height);

    if (keyboardHeight > minKeyboardHeight) {
      onKeyboardShown(view.getContext(), keyboardHeight);
    }
    return Math.max(keyboardHeight, minKeyboardHeight);
  }

  private static void onKeyboardShown(Context context, int keyboardHeight) {
    Log.w(TAG, "keyboard shown, height " + keyboardHeight);

    WindowManager wm = (WindowManager)context.getSystemService(Activity.WINDOW_SERVICE);
    if (wm == null || wm.getDefaultDisplay() == null) {
      return;
    }
    int rotation = wm.getDefaultDisplay().getRotation();

    switch (rotation) {
    case Surface.ROTATION_270:
    case Surface.ROTATION_90:
      setKeyboardLandscapeHeight(context, keyboardHeight);
      break;
    case Surface.ROTATION_0:
    case Surface.ROTATION_180:
      setKeyboardPortraitHeight(context, keyboardHeight);
    }
  }

  public static int getKeyboardHeight(Context context) {
    WindowManager      wm    = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    if (wm == null || wm.getDefaultDisplay() == null) {
      throw new AssertionError("WindowManager was null or there is no default display");
    }

    int rotation = wm.getDefaultDisplay().getRotation();

    switch (rotation) {
    case Surface.ROTATION_270:
    case Surface.ROTATION_90:
      return getKeyboardLandscapeHeight(context);
    case Surface.ROTATION_0:
    case Surface.ROTATION_180:
    default:
      return getKeyboardPortraitHeight(context);
    }
  }

  private static int getKeyboardLandscapeHeight(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
                            .getInt("keyboard_height_landscape",
                                    context.getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
  }

  private static int getKeyboardPortraitHeight(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
                            .getInt("keyboard_height_portrait",
                                    context.getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height));
  }

  private static void setKeyboardLandscapeHeight(Context context, int height) {
    PreferenceManager.getDefaultSharedPreferences(context)
                     .edit().putInt("keyboard_height_landscape", height).apply();
  }

  private static void setKeyboardPortraitHeight(Context context, int height) {
    PreferenceManager.getDefaultSharedPreferences(context)
                     .edit().putInt("keyboard_height_portrait", height).apply();
  }
}
