package org.thoughtcrime.securesms.video.exo

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player

fun ExoPlayer.configureForGifPlayback() {
  repeatMode = Player.REPEAT_MODE_ALL
  volume = 0f
}

fun ExoPlayer.configureForVideoPlayback() {
  repeatMode = Player.REPEAT_MODE_OFF
  volume = 1f
}
