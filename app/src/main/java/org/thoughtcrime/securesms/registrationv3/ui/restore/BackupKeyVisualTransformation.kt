/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual formatter for backup keys.
 *
 * @param length max length of key
 * @param chunkSize character count per group
 */
class BackupKeyVisualTransformation(private val length: Int, private val chunkSize: Int) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for (i in text.take(length).indices) {
      output += text[i]
      if (i % chunkSize == chunkSize - 1) {
        output += " "
      }
    }

    return TransformedText(
      text = AnnotatedString(output),
      offsetMapping = BackupKeyVisualTransformation(chunkSize)
    )
  }

  private class BackupKeyVisualTransformation(private val chunkSize: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      return offset + (offset / chunkSize)
    }

    override fun transformedToOriginal(offset: Int): Int {
      return offset - (offset / chunkSize)
    }
  }
}
