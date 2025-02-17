/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

interface CallControlsVisibilityListener {
  fun onShown()
  fun onHidden()

  companion object Empty : CallControlsVisibilityListener {
    override fun onShown() = Unit
    override fun onHidden() = Unit
  }
}
