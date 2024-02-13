/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.phonenumbers

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Formats the input number according to the regionCode. Assumes the input is all digits.
 */
class PhoneNumberVisualTransformation(
  regionCode: String
) : VisualTransformation {

  private val asYouTypeFormatter: AsYouTypeFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(regionCode)

  override fun filter(text: AnnotatedString): TransformedText {
    asYouTypeFormatter.clear()
    val output = text.map { asYouTypeFormatter.inputDigit(it) }.lastOrNull() ?: text.text

    return TransformedText(
      AnnotatedString(output),
      PhoneNumberOffsetMapping(output)
    )
  }

  /**
   * Each character in our phone number is either a digit or a transformed offset.
   */
  private class PhoneNumberOffsetMapping(
    private val transformed: String
  ) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      // We need a different algorithm here. We need to take UNTIL we've hit offset digits, and then return the resulting length.
      var remaining = (offset + 1)
      return transformed.takeWhile {
        if (it.isDigit()) {
          remaining--
        }

        remaining != 0
      }.length
    }

    override fun transformedToOriginal(offset: Int): Int {
      val substring = transformed.substring(0, offset)
      val characterCount = substring.count { !it.isDigit() }
      return offset - characterCount
    }
  }
}
