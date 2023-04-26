package org.thoughtcrime.securesms.scribbles.stickers

/**
 * Types of feature rich stickers for the image editor
 */
enum class FeatureSticker(val type: String) {
  DIGITAL_CLOCK("digital_clock"),
  ANALOG_CLOCK("analog_clock") ;

  companion object {
    @JvmStatic
    fun fromType(type: String) = FeatureSticker.values().first { it.type == type }
  }
}
