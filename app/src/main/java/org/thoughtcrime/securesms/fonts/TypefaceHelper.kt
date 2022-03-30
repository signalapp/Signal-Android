package org.thoughtcrime.securesms.fonts

import android.graphics.Typeface
import android.os.Build

/**
 * API independent, best-effort way of creating [Typeface]s of various family/weight.
 */
object TypefaceHelper {
  fun typefaceFor(family: Family, weightName: String, weight: Weight): Typeface {
    return when {
      Build.VERSION.SDK_INT >= 28 -> Typeface.create(
        Typeface.create(family.familyName, Typeface.NORMAL),
        weight.value,
        false
      )
      Build.VERSION.SDK_INT >= 21 -> Typeface.create("${family.familyName}-$weightName", Typeface.NORMAL)
      else -> Typeface.create(family.familyName, if (weight.value > Weight.MEDIUM.value) Typeface.BOLD else Typeface.NORMAL)
    }
  }

  enum class Family(val familyName: String) {
    SANS_SERIF("sans-serif"),
    SERIF("serif")
  }

  enum class Weight(val value: Int) {
    THIN(100),
    EXTRA_LIGHT(200),
    DEMI_LIGHT(200),
    LIGHT(300),
    NORMAL(400),
    MEDIUM(500),
    SEMI_BOLD(600),
    BOLD(700),
    EXTRA_BOLD(800),
    BLACK(900),
  }
}
