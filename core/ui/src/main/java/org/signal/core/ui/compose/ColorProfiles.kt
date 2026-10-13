/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Derives themed color states for controls that are painted in a color the color scheme knows nothing about, such as a
 * recipient's chat color. Colors from the scheme itself already have `on-` and container roles to pair with; these fill
 * the same gap for custom ones.
 */
object ColorProfiles {

  /** How much of a container's saturation survives into its disabled state. */
  private const val DISABLED_SATURATION = 0.3f

  /** How far a disabled container's lightness is pulled towards the surrounding chrome. */
  private const val DISABLED_LIGHTNESS_SHIFT = 0.6f

  /** Kept low so disabled content reads as inert against a container that has itself receded towards the chrome. */
  private const val DISABLED_CONTENT_ALPHA = 0.6f

  /**
   * A muted version of [color] for a control that is currently disabled, washed out and pulled towards the surrounding
   * chrome while keeping enough of its hue to stay recognizable.
   *
   * The result is opaque rather than translucent so it reads the same whatever it happens to sit on top of, which
   * matters for controls that float over content such as media or a wallpaper.
   *
   * Pair with [disabledContent].
   */
  @Composable
  fun disabledContainer(color: Color): Color {
    return color
      .scaleSaturation(DISABLED_SATURATION)
      .shiftLightnessToward(MaterialTheme.colorScheme.surfaceVariant, DISABLED_LIGHTNESS_SHIFT)
  }

  /**
   * The content color to draw on top of [disabledContainer]. Follows the chrome rather than the container's own hue,
   * because a container that has been pulled this far towards the chrome no longer reliably contrasts with whatever
   * the enabled content color was.
   */
  @Composable
  fun disabledContent(): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA)
  }
}
