/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.signal.core.models.media.MediaFolder

/**
 * Nav3 keys
 */
@Serializable
sealed interface MediaSendNavKey : NavKey {
  @Serializable
  sealed interface Select : MediaSendNavKey {
    @Serializable
    data object Folders : Select

    @Serializable
    data class Files(val folder: MediaFolder) : Select
  }

  @Serializable
  sealed interface Capture : MediaSendNavKey {
    @Serializable
    data object Chrome : Capture

    @Serializable
    data object Camera : Capture

    @Serializable
    data object TextStory : Capture
  }

  @Serializable
  data object Edit : MediaSendNavKey

  @Serializable
  data object Send : MediaSendNavKey
}
