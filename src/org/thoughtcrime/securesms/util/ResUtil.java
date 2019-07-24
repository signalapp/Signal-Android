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

import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.content.res.AppCompatResources;
import android.util.TypedValue;

public class ResUtil {

  public static int getColor(Context context, @AttrRes int attr) {
    final TypedArray styledAttributes = context.obtainStyledAttributes(new int[]{attr});
    final int        result           = styledAttributes.getColor(0, -1);
    styledAttributes.recycle();
    return result;
  }

  public static int getDrawableRes(Context c, @AttrRes int attr) {
    return getDrawableRes(c.getTheme(), attr);
  }

  public static int getDrawableRes(Theme theme, @AttrRes int attr) {
    final TypedValue out = new TypedValue();
    theme.resolveAttribute(attr, out, true);
    return out.resourceId;
  }

  public static Drawable getDrawable(Context c, @AttrRes int attr) {
    return AppCompatResources.getDrawable(c, getDrawableRes(c, attr));
  }

  public static int[] getResourceIds(Context c, @ArrayRes int array) {
    final TypedArray typedArray  = c.getResources().obtainTypedArray(array);
    final int[]      resourceIds = new int[typedArray.length()];
    for (int i = 0; i < typedArray.length(); i++) {
      resourceIds[i] = typedArray.getResourceId(i, 0);
    }
    typedArray.recycle();
    return resourceIds;
  }

  public static float getFloat(@NonNull Context context, @DimenRes int resId) {
    TypedValue value = new TypedValue();
    context.getResources().getValue(resId, value, true);
    return value.getFloat();
  }
}
