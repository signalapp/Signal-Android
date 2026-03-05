/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.app.Application
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.emoji.EmojiSource
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MemberLabelEmojiValidationTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private fun withEmojiSource(block: () -> Unit) {
    val source = EmojiSource.loadAssetBasedEmojis()
    mockkObject(EmojiSource) {
      every { EmojiSource.latest } returns source
      block()
    }
  }

  @Test
  fun `sanitizeEmoji returns valid emoji unchanged`() = withEmojiSource {
    assertEquals("\uD83D\uDE0D", MemberLabel.sanitizeEmoji("\uD83D\uDE0D")) // 😍
  }

  @Test
  fun `sanitizeEmoji returns valid ZWJ sequence unchanged`() = withEmojiSource {
    val familyEmoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66" // 👨‍👩‍👧‍
    assertEquals(familyEmoji, MemberLabel.sanitizeEmoji(familyEmoji))
  }

  @Test
  fun `sanitizeEmoji returns null for plain text`() = withEmojiSource {
    assertNull(MemberLabel.sanitizeEmoji("hello"))
  }

  @Test
  fun `sanitizeEmoji returns null for multiple emojis`() = withEmojiSource {
    assertNull(MemberLabel.sanitizeEmoji("\uD83D\uDE0D\uD83D\uDE0D")) // 😍😍
  }

  @Test
  fun `sanitizeEmoji returns null for emoji plus text`() = withEmojiSource {
    assertNull(MemberLabel.sanitizeEmoji("\uD83D\uDE0Dhi"))
  }

  @Test
  fun `sanitizeEmoji returns null for null input`() = withEmojiSource {
    assertNull(MemberLabel.sanitizeEmoji(null))
  }

  @Test
  fun `sanitizeEmoji returns null for empty string`() = withEmojiSource {
    assertNull(MemberLabel.sanitizeEmoji(""))
  }
}
