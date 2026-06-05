/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.compose.ui.text.AnnotatedString
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class AepVisualTransformationTest {

  @Test
  fun `filter - uppercases and groups characters without swapping display equivalents`() {
    val transformation = AepVisualTransformation(chunkSize = 4)

    val transformed = transformation.filter(AnnotatedString("a0O#=b"))

    assertThat(transformed.text).isEqualTo(AnnotatedString("A0O# =B"))
  }
}
