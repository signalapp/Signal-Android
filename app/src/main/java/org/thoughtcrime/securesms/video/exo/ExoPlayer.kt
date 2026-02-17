package org.thoughtcrime.securesms.video.exo

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

fun ExoPlayer.configureForGifPlayback() {
  repeatMode = Player.REPEAT_MODE_ALL
  volume = 0f
  trackSelectionParameters = trackSelectionParameters.buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
    .build()
}

fun ExoPlayer.configureForVideoPlayback() {
  repeatMode = Player.REPEAT_MODE_OFF
  volume = 1f
  trackSelectionParameters = trackSelectionParameters.buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    .build()
}
