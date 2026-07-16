/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Masks input with bullets exactly like [PasswordVisualTransformation], but is intentionally a
 * distinct type. Compose flags a field with password semantics whenever its transformation is a
 * [PasswordVisualTransformation], which makes the system autofill framework offer to save/fill a
 * password. We never want that prompt for PIN entry.
 */
object PinVisualTransformation : VisualTransformation {
  private const val MASK = '•'

  override fun filter(text: AnnotatedString): TransformedText {
    return TransformedText(
      AnnotatedString(MASK.toString().repeat(text.text.length)),
      OffsetMapping.Identity
    )
  }
}
