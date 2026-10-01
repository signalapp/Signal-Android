/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

interface CallControlsVisibilityListener {
  fun onShown()

  /**
   * @param isFullBleedCall Whether the call renders edge to edge, and so whether the system bars should
   *                        hide along with the controls. Decided by the Compose layer, which is the only
   *                        place that knows the window size class.
   */
  fun onHidden(isFullBleedCall: Boolean)

  companion object Empty : CallControlsVisibilityListener {
    override fun onShown() = Unit
    override fun onHidden(isFullBleedCall: Boolean) = Unit
  }
}
