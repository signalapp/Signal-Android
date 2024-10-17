/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.copied.androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color

@Immutable
class IconButtonColors internal constructor(
  private val containerColor: Color,
  private val contentColor: Color,
  private val disabledContainerColor: Color,
  private val disabledContentColor: Color
) {
  /**
   * Represents the container color for this icon button, depending on [enabled].
   *
   * @param enabled whether the icon button is enabled
   */
  @Composable
  internal fun containerColor(enabled: Boolean): State<Color> {
    return rememberUpdatedState(if (enabled) containerColor else disabledContainerColor)
  }

  /**
   * Represents the content color for this icon button, depending on [enabled].
   *
   * @param enabled whether the icon button is enabled
   */
  @Composable
  internal fun contentColor(enabled: Boolean): State<Color> {
    return rememberUpdatedState(if (enabled) contentColor else disabledContentColor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || other !is IconButtonColors) return false

    if (containerColor != other.containerColor) return false
    if (contentColor != other.contentColor) return false
    if (disabledContainerColor != other.disabledContainerColor) return false
    if (disabledContentColor != other.disabledContentColor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = containerColor.hashCode()
    result = 31 * result + contentColor.hashCode()
    result = 31 * result + disabledContainerColor.hashCode()
    result = 31 * result + disabledContentColor.hashCode()

    return result
  }
}

@Immutable
class IconToggleButtonColors internal constructor(
  private val containerColor: Color,
  private val contentColor: Color,
  private val disabledContainerColor: Color,
  private val disabledContentColor: Color,
  private val checkedContainerColor: Color,
  private val checkedContentColor: Color
) {
  /**
   * Represents the container color for this icon button, depending on [enabled] and [checked].
   *
   * @param enabled whether the icon button is enabled
   * @param checked whether the icon button is checked
   */
  @Composable
  internal fun containerColor(enabled: Boolean, checked: Boolean): State<Color> {
    val target = when {
      !enabled -> disabledContainerColor
      !checked -> containerColor
      else -> checkedContainerColor
    }
    return rememberUpdatedState(target)
  }

  /**
   * Represents the content color for this icon button, depending on [enabled] and [checked].
   *
   * @param enabled whether the icon button is enabled
   * @param checked whether the icon button is checked
   */
  @Composable
  internal fun contentColor(enabled: Boolean, checked: Boolean): State<Color> {
    val target = when {
      !enabled -> disabledContentColor
      !checked -> contentColor
      else -> checkedContentColor
    }
    return rememberUpdatedState(target)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || other !is IconToggleButtonColors) return false

    if (containerColor != other.containerColor) return false
    if (contentColor != other.contentColor) return false
    if (disabledContainerColor != other.disabledContainerColor) return false
    if (disabledContentColor != other.disabledContentColor) return false
    if (checkedContainerColor != other.checkedContainerColor) return false
    if (checkedContentColor != other.checkedContentColor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = containerColor.hashCode()
    result = 31 * result + contentColor.hashCode()
    result = 31 * result + disabledContainerColor.hashCode()
    result = 31 * result + disabledContentColor.hashCode()
    result = 31 * result + checkedContainerColor.hashCode()
    result = 31 * result + checkedContentColor.hashCode()

    return result
  }
}
