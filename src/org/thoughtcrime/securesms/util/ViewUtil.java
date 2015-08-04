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
import android.view.View;

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
}
