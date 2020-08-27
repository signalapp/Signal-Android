package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/**
 * Day/night UI mode related utilities.
 * @see <a href="https://developer.android.com/guide/topics/ui/look-and-feel/darktheme">Official Documentation</a>
 */
object UiModeUtilities {
    private const val PREF_KEY_SELECTED_UI_MODE = "SELECTED_UI_MODE"

    @JvmStatic
    public fun setUserSelectedUiMode(context: Context, uiMode: UiMode) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
                .putString(PREF_KEY_SELECTED_UI_MODE, uiMode.name)
                .apply()
        AppCompatDelegate.setDefaultNightMode(uiMode.nightModeValue)
    }

    @JvmStatic
    public fun getUserSelectedUiMode(context: Context): UiMode {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val selectedUiModeName = prefs.getString(PREF_KEY_SELECTED_UI_MODE, UiMode.SYSTEM_DEFAULT.name)!!
        var selectedUiMode: UiMode
        try {
            selectedUiMode = UiMode.valueOf(selectedUiModeName)
        } catch (e: IllegalArgumentException) {
            // Cannot recognize UiMode constant from the given string.
            selectedUiMode = UiMode.SYSTEM_DEFAULT
        }
        return selectedUiMode
    }

    @JvmStatic
    public fun setupUiModeToUserSelected(context: Context) {
        val selectedUiMode = getUserSelectedUiMode(context)
        setUserSelectedUiMode(context, selectedUiMode)
    }
}

//TODO Use localized string resources.
enum class UiMode(
        val displayName: String,
        val uiModeNightFlag: Int,
        val nightModeValue: Int) {

    DAY ("Day",
            Configuration.UI_MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_NO),

    NIGHT ("Night",
            Configuration.UI_MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_YES),

    SYSTEM_DEFAULT ("System default",
            Configuration.UI_MODE_NIGHT_UNDEFINED,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
}