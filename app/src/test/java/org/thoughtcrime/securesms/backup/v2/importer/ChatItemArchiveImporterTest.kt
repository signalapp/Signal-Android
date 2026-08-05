/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.archive.proto.ChatItem
import org.signal.archive.proto.RemoteDeletedMessage
import org.signal.core.models.backup.MediaRootBackupKey
import org.thoughtcrime.securesms.backup.v2.BackupMode
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatItemArchiveImporterTest {

  private val importState = ImportState(MediaRootBackupKey(ByteArray(32)), BackupMode.REMOTE).apply {
    remoteToLocalRecipientId[AUTHOR_ID] = RecipientId.from(2)
    chatIdToLocalRecipientId[CHAT_ID] = RecipientId.from(2)
    chatIdToLocalThreadId[CHAT_ID] = 1L
    chatIdToBackupRecipientId[CHAT_ID] = AUTHOR_ID
  }

  private val importer by lazy {
    ChatItemArchiveImporter(mockk<SQLiteDatabase>(relaxed = true), importState, batchSize = 100, clock = { NOW })
  }

  @Before
  fun setUp() {
    mockkObject(Recipient.Companion)
    every { Recipient.self() } returns Recipient(id = RecipientId.from(1), isResolving = false)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `skips message whose timer has already run out`() {
    importer.import(chatItem(expireStartDate = NOW - 2_000, expiresInMs = 1_000))

    assertThat(importer.flush()).isFalse()
  }

  @Test
  fun `skips message that expires exactly now`() {
    importer.import(chatItem(expireStartDate = NOW - 1_000, expiresInMs = 1_000))

    assertThat(importer.flush()).isFalse()
  }

  @Test
  fun `imports message whose timer has not run out`() {
    importer.import(chatItem(expireStartDate = NOW - 1_000, expiresInMs = 2_000))

    assertThat(importer.flush()).isTrue()
  }

  @Test
  fun `imports message whose timer has not started`() {
    importer.import(chatItem(expireStartDate = 0, expiresInMs = 1_000))

    assertThat(importer.flush()).isTrue()
  }

  @Test
  fun `imports message without a timer`() {
    importer.import(chatItem(expireStartDate = null, expiresInMs = null))

    assertThat(importer.flush()).isTrue()
  }

  private fun chatItem(expireStartDate: Long?, expiresInMs: Long?): ChatItem {
    return ChatItem(
      chatId = CHAT_ID,
      authorId = AUTHOR_ID,
      dateSent = 1_000,
      expireStartDate = expireStartDate,
      expiresInMs = expiresInMs,
      incoming = ChatItem.IncomingMessageDetails(dateReceived = 1_000),
      remoteDeletedMessage = RemoteDeletedMessage()
    )
  }

  companion object {
    private const val NOW = 1_000_000L
    private const val CHAT_ID = 10L
    private const val AUTHOR_ID = 20L
  }
}
