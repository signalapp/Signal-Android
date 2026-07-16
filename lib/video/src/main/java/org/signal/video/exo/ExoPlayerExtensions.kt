/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.video.exo

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

fun ExoPlayer.configureForVideoPlayback() {
  repeatMode = Player.REPEAT_MODE_OFF
  volume = 1f
  trackSelectionParameters = trackSelectionParameters.buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    .build()
}
