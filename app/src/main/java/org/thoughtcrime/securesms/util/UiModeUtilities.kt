package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.res.Configuration

/**
 * Day/night UI mode related utilities.
 * @see <a href="https://developer.android.com/guide/topics/ui/look-and-feel/darktheme">Official Documentation</a>
 */
object UiModeUtilities {

    /**
     * Whether the application UI is in the light mode
     * (do not confuse with the user selected UiMode).
     */
    @JvmStatic
    fun isDayUiMode(context: Context): Boolean {
        val uiModeNightBit = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiModeNightBit == Configuration.UI_MODE_NIGHT_NO
    }

}