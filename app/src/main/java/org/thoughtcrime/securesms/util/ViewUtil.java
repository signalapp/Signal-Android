/**
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.Lifecycle;

import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.views.Stub;

public final class ViewUtil {

  private ViewUtil() {
  }

  public static void focusAndShowKeyboard(@NonNull View view) {
    view.requestFocus();
    if (view.hasWindowFocus()) {
      showTheKeyboardNow(view);
    } else {
      view.getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
          if (hasFocus) {
            showTheKeyboardNow(view);
            view.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
          }
        }
      });
    }
  }

  private static void showTheKeyboardNow(@NonNull View view) {
    if (view.isFocused()) {
      view.post(() -> {
        InputMethodManager inputMethodManager = ServiceUtil.getInputMethodManager(view.getContext());
        inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
      });
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends View> T inflateStub(@NonNull View parent, @IdRes int stubId) {
    return (T)((ViewStub)parent.findViewById(stubId)).inflate();
  }

  public static <T extends View> Stub<T> findStubById(@NonNull Activity parent, @IdRes int resId) {
    return new Stub<>(parent.findViewById(resId));
  }

  private static Animation getAlphaAnimation(float from, float to, int duration) {
    final Animation anim = new AlphaAnimation(from, to);
    anim.setInterpolator(new FastOutSlowInInterpolator());
    anim.setDuration(duration);
    return anim;
  }

  public static void fadeIn(final @NonNull View view, final int duration) {
    animateIn(view, getAlphaAnimation(0f, 1f, duration));
  }

  public static ListenableFuture<Boolean> fadeOut(final @NonNull View view, final int duration) {
    return fadeOut(view, duration, View.GONE);
  }

  public static ListenableFuture<Boolean> fadeOut(@NonNull View view, int duration, int visibility) {
    return animateOut(view, getAlphaAnimation(1f, 0f, duration), visibility);
  }

  public static ListenableFuture<Boolean> animateOut(final @NonNull View view, final @NonNull Animation animation, final int visibility) {
    final SettableFuture future = new SettableFuture();
    if (view.getVisibility() == visibility) {
      future.set(true);
    } else {
      view.clearAnimation();
      animation.reset();
      animation.setStartTime(0);
      animation.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationRepeat(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
          view.setVisibility(visibility);
          future.set(true);
        }
      });
      view.startAnimation(animation);
    }
    return future;
  }

  public static void animateIn(final @NonNull View view, final @NonNull Animation animation) {
    if (view.getVisibility() == View.VISIBLE) return;

    view.clearAnimation();
    animation.reset();
    animation.setStartTime(0);
    view.setVisibility(View.VISIBLE);
    view.startAnimation(animation);
  }

  @SuppressWarnings("unchecked")
  public static <T extends View> T inflate(@NonNull   LayoutInflater inflater,
                                           @NonNull   ViewGroup      parent,
                                           @LayoutRes int            layoutResId)
  {
    return (T)(inflater.inflate(layoutResId, parent, false));
  }

  @SuppressLint("RtlHardcoded")
  public static void setTextViewGravityStart(final @NonNull TextView textView, @NonNull Context context) {
    if (DynamicLanguage.getLayoutDirection(context) == View.LAYOUT_DIRECTION_RTL) {
      textView.setGravity(Gravity.RIGHT);
    } else {
      textView.setGravity(Gravity.LEFT);
    }
  }

  public static void mirrorIfRtl(View view, Context context) {
    if (DynamicLanguage.getLayoutDirection(context) == View.LAYOUT_DIRECTION_RTL) {
      view.setScaleX(-1.0f);
    }
  }

  public static float pxToDp(float px) {
    return px / Resources.getSystem().getDisplayMetrics().density;
  }

  public static int dpToPx(Context context, int dp) {
    return (int)((dp * context.getResources().getDisplayMetrics().density) + 0.5);
  }

  public static int dpToPx(int dp) {
    return Math.round(dp * Resources.getSystem().getDisplayMetrics().density);
  }

  public static int dpToSp(int dp) {
    return (int) (dpToPx(dp) / Resources.getSystem().getDisplayMetrics().scaledDensity);
  }

  public static int spToPx(float sp) {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, Resources.getSystem().getDisplayMetrics());
  }

  public static void updateLayoutParams(@NonNull View view, int width, int height) {
    view.getLayoutParams().width  = width;
    view.getLayoutParams().height = height;
    view.requestLayout();
  }

  public static void updateLayoutParamsIfNonNull(@Nullable View view, int width, int height) {
    if (view != null) {
      updateLayoutParams(view, width, height);
    }
  }

  public static int getLeftMargin(@NonNull View view) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin;
    }
    return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin;
  }

  public static int getRightMargin(@NonNull View view) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin;
    }
    return ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin;
  }

  public static void setLeftMargin(@NonNull View view, int margin) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin = margin;
    } else {
      ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin = margin;
    }
    view.forceLayout();
    view.requestLayout();
  }

  public static void setRightMargin(@NonNull View view, int margin) {
    if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).rightMargin = margin;
    } else {
      ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).leftMargin = margin;
    }
    view.forceLayout();
    view.requestLayout();
  }

  public static void setTopMargin(@NonNull View view, int margin) {
    ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin = margin;
    view.requestLayout();
  }

  public static void setPaddingTop(@NonNull View view, int padding) {
    view.setPadding(view.getPaddingLeft(), padding, view.getPaddingRight(), view.getPaddingBottom());
  }

  public static void setPaddingBottom(@NonNull View view, int padding) {
    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), padding);
  }

  public static void setPadding(@NonNull View view, int padding) {
    view.setPadding(padding, padding, padding, padding);
  }

  public static void setPaddingStart(@NonNull View view, int padding) {
    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
      view.setPadding(padding, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
    } else {
      view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), padding, view.getPaddingBottom());
    }
  }

  public static void setPaddingEnd(@NonNull View view, int padding) {
    if (view.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR) {
      view.setPadding(padding, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
    } else {
      view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), padding, view.getPaddingBottom());
    }
  }

  public static boolean isPointInsideView(@NonNull View view, float x, float y) {
    int[] location = new int[2];

    view.getLocationOnScreen(location);

    int viewX = location[0];
    int viewY = location[1];

    return x > viewX && x < viewX + view.getWidth() &&
           y > viewY && y < viewY + view.getHeight();
  }

  public static int getStatusBarHeight(@NonNull View view) {
    int result = 0;
    int resourceId = view.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = view.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  public static void hideKeyboard(@NonNull Context context, @NonNull View view) {
    InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
    inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  /**
   * Enables or disables a view and all child views recursively.
   */
  public static void setEnabledRecursive(@NonNull View view, boolean enabled) {
    view.setEnabled(enabled);
    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        setEnabledRecursive(viewGroup.getChildAt(i), enabled);
      }
    }
  }

  public static @Nullable Lifecycle getActivityLifecycle(@NonNull View view) {
    return getActivityLifecycle(view.getContext());
  }

  private static @Nullable Lifecycle getActivityLifecycle(@Nullable Context context) {
    if (context instanceof ContextThemeWrapper) {
      return getActivityLifecycle(((ContextThemeWrapper) context).getBaseContext());
    }

    if (context instanceof AppCompatActivity) {
      return ((AppCompatActivity) context).getLifecycle();
    }

    return null;
  }
}
