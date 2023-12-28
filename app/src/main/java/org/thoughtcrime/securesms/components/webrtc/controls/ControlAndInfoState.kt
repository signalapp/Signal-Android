/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import androidx.compose.runtime.Immutable

@Immutable
data class ControlAndInfoState(
  val resetScrollState: Long = 0
)
