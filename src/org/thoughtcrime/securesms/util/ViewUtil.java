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

import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.widget.TextView;

public class ViewUtil {
  public static void setBackgroundSavingPadding(View v, Drawable drawable) {
    final int paddingBottom = v.getPaddingBottom();
    final int paddingLeft = v.getPaddingLeft();
    final int paddingRight = v.getPaddingRight();
    final int paddingTop = v.getPaddingTop();
    v.setBackgroundDrawable(drawable);
    v.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
  }

  public static void setBackgroundSavingPadding(View v, @DrawableRes int resId) {
    final int paddingBottom = v.getPaddingBottom();
    final int paddingLeft = v.getPaddingLeft();
    final int paddingRight = v.getPaddingRight();
    final int paddingTop = v.getPaddingTop();
    v.setBackgroundResource(resId);
    v.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
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

  public static void animateOut(final @NonNull View view, final @NonNull Animation animation) {
    if (view.getVisibility() == View.GONE) return;

    view.clearAnimation();
    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override public void onAnimationStart(Animation animation) {}
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationEnd(Animation animation) {
        view.setVisibility(View.GONE);
      }
    });

    view.startAnimation(animation);
  }

  public static void animateIn(final @NonNull View view, final @NonNull Animation animation) {
    if (view.getVisibility() == View.VISIBLE) return;
    view.clearAnimation();
    view.setVisibility(View.VISIBLE);
    view.startAnimation(animation);
  }
}
