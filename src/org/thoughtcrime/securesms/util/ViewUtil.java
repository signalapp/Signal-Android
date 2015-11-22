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

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class ViewUtil {
  @SuppressWarnings("deprecation")
  public static void setBackground(final @NonNull View v, final @Nullable Drawable drawable) {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      v.setBackground(drawable);
    } else {
      v.setBackgroundDrawable(drawable);
    }
  }

  public static void setY(final @NonNull View v, final int y) {
    if (VERSION.SDK_INT >= 11) {
      ViewCompat.setY(v, y);
    } else {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
      params.topMargin = y;
      v.setLayoutParams(params);
    }
  }

  public static float getY(final @NonNull View v) {
    if (VERSION.SDK_INT >= 11) {
      return ViewCompat.getY(v);
    } else {
      return ((ViewGroup.MarginLayoutParams)v.getLayoutParams()).topMargin;
    }
  }

  public static void setX(final @NonNull View v, final int x) {
    if (VERSION.SDK_INT >= 11) {
      ViewCompat.setX(v, x);
    } else {
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
      params.leftMargin = x;
      v.setLayoutParams(params);
    }
  }

  public static float getX(final @NonNull View v) {
    if (VERSION.SDK_INT >= 11) {
      return ViewCompat.getX(v);
    } else {
      return ((LayoutParams)v.getLayoutParams()).leftMargin;
    }
  }

  public static void swapChildInPlace(ViewGroup parent, View toRemove, View toAdd, int defaultIndex) {
    int childIndex = parent.indexOfChild(toRemove);
    if (childIndex > -1) parent.removeView(toRemove);
    parent.addView(toAdd, childIndex > -1 ? childIndex : defaultIndex);
  }

  public static CharSequence ellipsize(@Nullable CharSequence text, @NonNull TextView view) {
    if (TextUtils.isEmpty(text) || view.getWidth() == 0 || view.getEllipsize() != TruncateAt.END) {
      return text;
    } else {
      return TextUtils.ellipsize(text,
                                 view.getPaint(),
                                 view.getWidth() - view.getPaddingRight() - view.getPaddingLeft(),
                                 TruncateAt.END);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends View> T inflateStub(@NonNull View parent, @IdRes int stubId) {
    return (T)((ViewStub)parent.findViewById(stubId)).inflate();
  }

  @SuppressWarnings("unchecked")
  public static <T extends View> T findById(@NonNull View parent, @IdRes int resId) {
    return (T) parent.findViewById(resId);
  }

  @SuppressWarnings("unchecked")
  public static <T extends View> T findById(@NonNull Activity parent, @IdRes int resId) {
    return (T) parent.findViewById(resId);
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
}
