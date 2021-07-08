package org.thoughtcrime.securesms.loki.utilities

import android.content.res.Resources
import android.os.Build
import androidx.annotation.ColorRes
import kotlin.math.roundToInt

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}

fun toPx(dp: Int, resources: Resources): Int {
    return toPx(dp.toFloat(), resources).roundToInt()
}

fun toPx(dp: Float, resources: Resources): Float {
    val scale = resources.displayMetrics.density
    return (dp * scale)
}

fun toDp(px: Int, resources: Resources): Int {
    return toDp(px.toFloat(), resources).roundToInt()
}

fun toDp(px: Float, resources: Resources): Float {
    val scale = resources.displayMetrics.density
    return (px / scale)
}
