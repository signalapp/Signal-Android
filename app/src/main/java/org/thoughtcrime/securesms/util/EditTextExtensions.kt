package org.thoughtcrime.securesms.util

import android.widget.EditText

/**
 * Since this value is only supported on API26+ we hard-code it here
 *  to avoid issues with older versions. This mirrors the approach
 *  taken by [org.thoughtcrime.securesms.components.ComposeText].
 */
private const val INCOGNITO_KEYBOARD = 16777216

/**
 * Enables or disables incognito-mode for the keyboard. Note that this might not
 * be respected by all IMEs.
 */
fun EditText.setIncognitoKeyboardEnabled(isIncognitoKeyboardEnabled: Boolean) {
  imeOptions = if (isIncognitoKeyboardEnabled) {
    imeOptions or INCOGNITO_KEYBOARD
  } else {
    imeOptions and INCOGNITO_KEYBOARD.inv()
  }
}
