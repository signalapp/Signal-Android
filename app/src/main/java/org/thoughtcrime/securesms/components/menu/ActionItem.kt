package org.thoughtcrime.securesms.components.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Represents an action to be rendered via [SignalContextMenu] or [SignalBottomActionBar]
 */
data class ActionItem(
  @DrawableRes val iconRes: Int,
  val title: CharSequence,
  val action: Runnable
)
