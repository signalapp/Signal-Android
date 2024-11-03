package org.thoughtcrime.securesms.keyboard.gif

import org.thoughtcrime.securesms.R

enum class GifQuickSearchOption(private val rank: Int, val image: Int, val query: String) {
  TRENDING(0, R.drawable.ic_gif_trending_24, ""),
  CELEBRATE(1, R.drawable.ic_gif_celebrate_24, "celebrate"),
  LOVE(2, R.drawable.ic_gif_love_24, "love"),
  THUMBS_UP(3, R.drawable.ic_gif_thumbsup_24, "thumbs up"),
  SURPRISED(4, R.drawable.ic_gif_surprised_24, "surprised"),
  EXCITED(5, R.drawable.ic_gif_excited_24, "excited"),
  SAD(6, R.drawable.ic_gif_sad_24, "sad"),
  ANGRY(7, R.drawable.ic_gif_angry_24, "angry");

  companion object {
    val ranked: List<GifQuickSearchOption> by lazy { entries.sortedBy { it.rank } }
  }
}
