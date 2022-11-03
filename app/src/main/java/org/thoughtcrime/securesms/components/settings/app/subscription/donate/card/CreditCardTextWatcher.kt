package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.text.Editable
import android.text.TextWatcher

/**
 * Formats a credit card by type as the user modifies it.
 */
class CreditCardTextWatcher : TextWatcher {

  private var isBackspace: Boolean = false

  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    isBackspace = count == 0
  }

  override fun afterTextChanged(s: Editable) {
    val userInput = s.toString()
    val normalizedNumber = userInput.filter { it != ' ' }

    val formattedNumber = when (CreditCardType.fromCardNumber(normalizedNumber)) {
      CreditCardType.AMERICAN_EXPRESS -> applyAmexFormatting(normalizedNumber)
      CreditCardType.UNIONPAY -> applyUnionPayFormatting(normalizedNumber)
      CreditCardType.OTHER -> applyOtherFormatting(normalizedNumber)
    }

    val backspaceHandled = if (isBackspace && formattedNumber.endsWith(' ') && formattedNumber.length > userInput.length) {
      formattedNumber.dropLast(2)
    } else {
      formattedNumber
    }

    if (userInput != backspaceHandled) {
      s.replace(0, s.length, backspaceHandled)
    }
  }

  private fun applyAmexFormatting(normalizedNumber: String): String {
    return applyGrouping(normalizedNumber, listOf(4, 6, 5))
  }

  private fun applyUnionPayFormatting(normalizedNumber: String): String {
    return when {
      normalizedNumber.length <= 13 -> applyGrouping(normalizedNumber, listOf(4, 4, 5))
      normalizedNumber.length <= 16 -> applyGrouping(normalizedNumber, listOf(4, 4, 4, 4))
      else -> applyGrouping(normalizedNumber, listOf(5, 5, 5, 4))
    }
  }

  private fun applyOtherFormatting(normalizedNumber: String): String {
    return if (normalizedNumber.length <= 16) {
      applyGrouping(normalizedNumber, listOf(4, 4, 4, 4))
    } else {
      applyGrouping(normalizedNumber, listOf(5, 5, 5, 4))
    }
  }

  private fun applyGrouping(normalizedNumber: String, groups: List<Int>): String {
    val maxCardLength = groups.sum()

    return groups.fold(0 to emptyList<String>()) { acc, limit ->
      val offset = acc.first
      val section = normalizedNumber.drop(offset).take(limit)
      val segment = if (limit == section.length && offset + limit != maxCardLength) {
        "$section "
      } else {
        section
      }

      (offset + limit) to acc.second + segment
    }.second.filter { it.isNotEmpty() }.joinToString("")
  }
}
