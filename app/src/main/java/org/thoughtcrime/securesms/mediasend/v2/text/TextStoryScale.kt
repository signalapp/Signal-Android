package org.thoughtcrime.securesms.mediasend.v2.text

object TextStoryScale {
  fun convertToScale(textScale: Int): Float {
    if (textScale < 0) {
      return 1f
    }

    val minimumScale = 0.5f
    val maximumScale = 1.5f
    val scaleRange = maximumScale - minimumScale

    val percent = textScale / 100f
    val scale = scaleRange * percent + minimumScale

    return scale
  }
}
