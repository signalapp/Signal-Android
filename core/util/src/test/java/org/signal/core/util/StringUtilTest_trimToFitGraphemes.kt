/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StringUtilTest_trimToFitGraphemes {

  companion object {
    /** One grapheme cluster, but 25 UTF-8 bytes. */
    private const val FAMILY = "👨‍👩‍👧‍👦"
  }

  @Test
  fun `null and empty text trim to empty`() {
    assertThat(StringUtil.trimToFit(null, maxGraphemes = 10, maxByteLength = 10)).isEqualTo("")
    assertThat(StringUtil.trimToFit("", maxGraphemes = 10, maxByteLength = 10)).isEqualTo("")
  }

  @Test
  fun `text within both limits is unchanged`() {
    assertThat(StringUtil.trimToFit("Speak freely", maxGraphemes = 20, maxByteLength = 20)).isEqualTo("Speak freely")
  }

  @Test
  fun `text is capped in grapheme clusters`() {
    assertThat(StringUtil.trimToFit("Speak freely", maxGraphemes = 5, maxByteLength = 100)).isEqualTo("Speak")
  }

  @Test
  fun `a multi-codepoint emoji counts as one grapheme cluster`() {
    assertThat(StringUtil.trimToFit("$FAMILY$FAMILY", maxGraphemes = 1, maxByteLength = 100)).isEqualTo(FAMILY)
  }

  @Test
  fun `text is capped in bytes after graphemes, without splitting a cluster`() {
    assertThat(StringUtil.trimToFit("ab$FAMILY", maxGraphemes = 10, maxByteLength = 10)).isEqualTo("ab")
  }
}
