package org.signal.core.util

import androidx.annotation.Px

/**
 * Converts the given Float DP value into Pixels.
 */
@get:Px
val Float.dp: Float get() = DimensionUnit.DP.toPixels(this)

/**
 * Converts the given Int DP value into Pixels
 */
@get:Px
val Int.dp: Int get() = this.toFloat().dp.toInt()