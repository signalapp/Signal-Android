package org.thoughtcrime.securesms.loki

import android.content.res.Resources
import android.os.Build
import android.support.annotation.ColorRes

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}