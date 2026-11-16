/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.emoji.EmojiSource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.DataMessage

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DataMessageProcessorTest_stickers {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    val emojiSource = EmojiSource.loadAssetBasedEmojis()
    mockkObject(EmojiSource)
    every { EmojiSource.latest } returns emojiSource

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.stickers } returns mockk {
      every { getSticker(any(), any(), any()) } returns null
    }
  }

  @After
  fun tearDown() {
    unmockkObject(SignalDatabase.Companion)
    unmockkObject(EmojiSource)
  }

  @Test
  fun getStickerAttachment_whenEmojiIsValid_keepEmoji() {
    assertThat(getStickerEmoji("😀")).isEqualTo("😀")
  }

  @Test
  fun getStickerAttachment_whenEmojiIsNull_emojiIsNull() {
    assertThat(getStickerEmoji(null)).isNull()
  }

  @Test
  fun getStickerAttachment_whenEmojiIsNotAnEmoji_dropEmoji() {
    assertThat(getStickerEmoji("Click here to verify your account")).isNull()
  }

  @Test
  fun getStickerAttachment_whenEmojiIsTooLong_dropEmoji() {
    assertThat(getStickerEmoji("😀".repeat(100))).isNull()
  }

  private fun getStickerEmoji(emoji: String?): String? {
    val message = DataMessage(
      sticker = DataMessage.Sticker(
        packId = ByteArray(16) { 1 }.toByteString(),
        packKey = ByteArray(32) { 2 }.toByteString(),
        stickerId = 1,
        emoji = emoji,
        data_ = AttachmentPointer(
          cdnKey = "asdf",
          cdnNumber = 3,
          key = ByteArray(64) { 3 }.toByteString(),
          contentType = "image/webp",
          size = 1024,
          digest = ByteArray(32) { 4 }.toByteString()
        )
      )
    )

    val attachment = DataMessageProcessor.getStickerAttachment(System.currentTimeMillis(), message)
    assertThat(attachment).isNotNull()
    return attachment!!.stickerLocator!!.emoji
  }
}
