package org.thoughtcrime.securesms.keyboard.gif

import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

enum class GifQuickSearchOption(private val rank: Int, val image: Int, val query: String, @StringRes val categoryLabel: Int) {
  TRENDING(0, R.drawable.ic_gif_trending_24, "", R.string.GifQuickSearchOption__trending),
  CELEBRATE(1, R.drawable.ic_gif_celebrate_24, "celebrate", R.string.GifQuickSearchOption__celebrate),
  LOVE(2, R.drawable.ic_gif_love_24, "love", R.string.GifQuickSearchOption__love),
  THUMBS_UP(3, R.drawable.ic_gif_thumbsup_24, "thumbs up", R.string.GifQuickSearchOption__thumbs_up),
  SURPRISED(4, R.drawable.ic_gif_surprised_24, "surprised", R.string.GifQuickSearchOption__surprised),
  EXCITED(5, R.drawable.ic_gif_excited_24, "excited", R.string.GifQuickSearchOption__excited),
  SAD(6, R.drawable.ic_gif_sad_24, "sad", R.string.GifQuickSearchOption__sad),
  ANGRY(7, R.drawable.ic_gif_angry_24, "angry", R.string.GifQuickSearchOption__angry);

  companion object {
    val ranked: List<GifQuickSearchOption> by lazy { entries.sortedBy { it.rank } }
  }
}
