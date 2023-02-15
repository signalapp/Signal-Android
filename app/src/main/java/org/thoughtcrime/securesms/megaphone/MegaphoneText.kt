package org.thoughtcrime.securesms.megaphone

import android.content.Context
import androidx.annotation.StringRes

/**
 * Allows setting of megaphone text by string resource or string literal.
 */
data class MegaphoneText(@StringRes private val stringRes: Int = 0, private val string: String? = null) {
  @get:JvmName("hasText")
  val hasText = stringRes != 0 || string != null

  fun resolve(context: Context): String? {
    return if (stringRes != 0) context.getString(stringRes) else string
  }

  companion object {
    @JvmStatic
    fun from(@StringRes stringRes: Int): MegaphoneText = MegaphoneText(stringRes)

    @JvmStatic
    fun from(string: String): MegaphoneText = MegaphoneText(string = string)
  }
}
