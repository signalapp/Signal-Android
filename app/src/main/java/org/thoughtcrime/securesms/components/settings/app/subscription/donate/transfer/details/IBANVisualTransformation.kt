/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Transforms the given input string to an IBAN representative format:
 *
 * AB1234567890 becomes AB12 3456 7890
 */
object IBANVisualTransformation : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for (i in text.take(34).indices) {
      output += text[i]
      if (i % 4 == 3) {
        output += " "
      }
    }

    return TransformedText(
      text = AnnotatedString(output),
      offsetMapping = IBANOffsetMapping
    )
  }

  private object IBANOffsetMapping : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      return offset + (offset / 4)
    }

    override fun transformedToOriginal(offset: Int): Int {
      return offset - (offset / 5)
    }
  }
}
