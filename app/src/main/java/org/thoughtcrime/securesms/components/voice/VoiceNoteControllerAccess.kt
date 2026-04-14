/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.thoughtcrime.securesms.components.voice

import android.content.Context
import androidx.media3.session.MediaSession

internal enum class VoiceNoteControllerAccessLevel {
  INTERNAL,
  TRUSTED_EXTERNAL,
  REJECTED
}

internal object VoiceNoteControllerAccess {

  /**
   * The app itself needs full control to enqueue and resolve voice notes. Everyone else is limited
   * to MediaSession's trusted controller model, which preserves system integrations but blocks
   * arbitrary third-party apps from driving private playlist construction.
   */
  @JvmStatic
  fun levelFor(context: Context, controller: MediaSession.ControllerInfo): VoiceNoteControllerAccessLevel {
    return when {
      controller.packageName == context.packageName -> VoiceNoteControllerAccessLevel.INTERNAL
      controller.isTrusted -> VoiceNoteControllerAccessLevel.TRUSTED_EXTERNAL
      else -> VoiceNoteControllerAccessLevel.REJECTED
    }
  }
}
