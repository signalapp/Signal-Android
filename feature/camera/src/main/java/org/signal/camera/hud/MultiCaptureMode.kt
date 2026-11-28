/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

/**
 * Whether the HUD offers multi-capture, and if so whether it is on. Owned by the host, since what a capture leads to
 * afterwards is the host's to decide.
 */
enum class MultiCaptureMode {
  NONE,
  ON,
  OFF
}
