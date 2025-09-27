/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual formatter for backup keys.
 *
 * @param chunkSize character count per group
 */
class BackupKeyVisualTransformation(private val chunkSize: Int) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for ((i, c) in text.withIndex()) {
      output += c
      if (i % chunkSize == chunkSize - 1) {
        output += " "
      }
    }

    val transformed = output.trimEnd()

    return TransformedText(
      text = AnnotatedString(transformed),
      offsetMapping = BackupKeyVisualTransformation(chunkSize, text.length)
    )
  }

  private class BackupKeyVisualTransformation(private val chunkSize: Int, private val inputSize: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      val transformed = offset + (offset / chunkSize)

      return when {
        inputSize == 0 -> 0
        offset == inputSize && offset >= chunkSize && offset % chunkSize == 0 -> transformed - 1
        else -> transformed
      }
    }

    override fun transformedToOriginal(offset: Int): Int {
      return offset - (offset / (chunkSize + 1))
    }
  }
}
