package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.text.Editable
import android.text.TextWatcher

class CreditCardExpirationTextWatcher : TextWatcher {

  private var isBackspace = false

  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    isBackspace = count == 0
  }

  override fun afterTextChanged(s: Editable) {
    val text = s.toString()
    val formattedText = when (text.length) {
      1 -> formatForSingleCharacter(text)
      2 -> formatForTwoCharacters(text)
      else -> text
    }

    val finalText = if (isBackspace && text.length < formattedText.length && formattedText.endsWith("/")) {
      formattedText.dropLast(2)
    } else {
      formattedText
    }

    if (finalText != text) {
      s.replace(0, s.length, finalText)
    }
  }

  private fun formatForSingleCharacter(text: String): String {
    val number = text.toIntOrNull() ?: return text
    return if (number > 1) {
      "0$number/"
    } else {
      text
    }
  }

  private fun formatForTwoCharacters(text: String): String {
    val number = text.toIntOrNull() ?: return text
    return if (number <= 12) {
      "%02d/".format(number)
    } else {
      text
    }
  }
}
