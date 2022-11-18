package org.thoughtcrime.securesms.components.settings

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.util.SpanUtil

sealed class DSLSettingsText {

  private data class FromResource(
    @StringRes private val stringId: Int,
    @ColorInt private val textColor: Int?
  ) : DSLSettingsText() {
    override fun resolve(context: Context): CharSequence {
      val text = context.getString(stringId)

      return if (textColor == null) {
        text
      } else {
        SpanUtil.color(textColor, text)
      }
    }
  }

  private data class FromCharSequence(private val charSequence: CharSequence) : DSLSettingsText() {
    override fun resolve(context: Context): CharSequence = charSequence
  }

  abstract fun resolve(context: Context): CharSequence

  companion object {
    fun from(@StringRes stringId: Int, @ColorInt textColor: Int? = null): DSLSettingsText =
      FromResource(stringId, textColor)

    fun from(charSequence: CharSequence): DSLSettingsText = FromCharSequence(charSequence)
  }
}
