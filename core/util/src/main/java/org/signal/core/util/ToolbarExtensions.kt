/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuItemCompat
import androidx.core.view.forEach

fun Toolbar.setActionItemTint(@ColorInt tint: Int) {
  menu.forEach {
    MenuItemCompat.setIconTintList(it, ColorStateList.valueOf(tint))
  }

  navigationIcon?.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
  overflowIcon?.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
}
