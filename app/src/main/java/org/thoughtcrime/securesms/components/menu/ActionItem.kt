package org.thoughtcrime.securesms.components.menu

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import org.signal.core.ui.R as CoreUiR

/**
 * Represents an action to be rendered via [SignalContextMenu] or [SignalBottomActionBar]
 */
data class ActionItem @JvmOverloads constructor(
  @DrawableRes val iconRes: Int,
  val title: CharSequence,
  @ColorRes val tintRes: Int = CoreUiR.color.signal_colorOnSurface,
  val action: Runnable
)
