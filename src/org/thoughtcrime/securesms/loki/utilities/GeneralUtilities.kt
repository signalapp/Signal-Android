package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.support.annotation.ColorRes
import org.thoughtcrime.securesms.database.DatabaseFactory
import kotlin.math.roundToInt

fun Resources.getColorWithID(@ColorRes id: Int, theme: Resources.Theme?): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getColor(id, theme)
    } else {
        @Suppress("DEPRECATION") getColor(id)
    }
}

fun toPx(dp: Int, resources: Resources): Int {
    val scale = resources.displayMetrics.density
    return (dp * scale).roundToInt()
}

fun isPublicChat(context: Context, recipient: String): Boolean {
    return DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().values.map { it.server }.contains(recipient)
}
