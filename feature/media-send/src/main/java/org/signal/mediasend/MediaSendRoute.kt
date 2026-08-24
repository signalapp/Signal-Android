/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.signal.core.models.media.MediaFolder

/**
 * Using @Serializable and NavKey for type-safe navigation with Navigation 3.
 */
@Serializable
sealed interface MediaSendRoute : NavKey {
  @Serializable
  sealed interface Select : MediaSendRoute {
    @Serializable
    data object Folders : Select

    @Serializable
    data class Files(val folder: MediaFolder) : Select
  }

  @Serializable
  sealed interface Capture : MediaSendRoute {
    @Serializable
    data object Chrome : Capture

    @Serializable
    data object Camera : Capture

    @Serializable
    data object TextStory : Capture
  }

  @Serializable
  data object Edit : MediaSendRoute

  @Serializable
  data object Send : MediaSendRoute
}
