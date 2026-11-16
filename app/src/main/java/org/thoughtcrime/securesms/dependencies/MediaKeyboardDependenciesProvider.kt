/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import androidx.media3.exoplayer.ExoPlayer
import org.signal.mediakeyboard.MediaKeyboardDependencies
import org.signal.video.exo.ExoPlayerPool

object MediaKeyboardDependenciesProvider : MediaKeyboardDependencies.Provider {
  override fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer> = AppDependencies.exoPlayerPool
}
