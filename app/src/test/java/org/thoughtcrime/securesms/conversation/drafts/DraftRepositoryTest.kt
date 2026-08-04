/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.drafts

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.DirectExecutor
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

/**
 * Verifies that a share payload is handed to the conversation exactly once, using the durable marker in
 * [org.thoughtcrime.securesms.keyvalue.MiscellaneousValues.lastProcessedShareDataTimestamp].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DraftRepositoryTest {

  companion object {
    private const val THREAD_ID = 1L
  }

  @get:Rule
  val signalStore = SignalStoreRule()

  private lateinit var draftTable: DraftTable

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    // Another test sharing this Robolectric sandbox may have already initialized Schedulers.io() to a
    // TestScheduler that nothing advances, which would make load()'s blockingGet() wait forever. Run inline.
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }

    draftTable = mockk(relaxed = true)
    every { draftTable.getDrafts(any()) } returns DraftTable.Drafts()
  }

  @After
  fun tearDown() {
    RxJavaPlugins.setIoSchedulerHandler(null)
  }

  @Test
  fun `unprocessed share is delivered and advances the marker`() {
    val result = repository(shareDataTimestamp = 1000L, draftText = "shared text").load()

    assertEquals(DraftRepository.ShareOrDraftData.SetText("shared text"), result?.first)
    assertEquals(1000L, SignalStore.misc.lastProcessedShareDataTimestamp)
  }

  @Test
  fun `share is not delivered a second time`() {
    SignalStore.misc.lastProcessedShareDataTimestamp = 1000L

    val result = repository(shareDataTimestamp = 1000L, draftText = "shared text").load()

    assertNull(result?.first)
    assertEquals(1000L, SignalStore.misc.lastProcessedShareDataTimestamp)
  }

  /** AND-9817: a restored navigation entry can carry a share that is older than the last one we consumed. */
  @Test
  fun `share older than the marker is not delivered`() {
    SignalStore.misc.lastProcessedShareDataTimestamp = 2000L

    val result = repository(shareDataTimestamp = 1000L, draftText = "shared text").load()

    assertNull(result?.first)
    assertEquals(2000L, SignalStore.misc.lastProcessedShareDataTimestamp)
  }

  @Test
  fun `skipped share still loads database drafts`() {
    SignalStore.misc.lastProcessedShareDataTimestamp = 1000L
    every { draftTable.getDrafts(THREAD_ID) } returns DraftTable.Drafts(listOf(DraftTable.Draft(DraftTable.Draft.TEXT, "saved draft")))

    val result = repository(shareDataTimestamp = 1000L, draftText = "shared text").load()

    assertEquals(DraftRepository.ShareOrDraftData.SetText("saved draft"), result?.first)
  }

  @Test
  fun `opening a conversation without share data does not reset the marker`() {
    SignalStore.misc.lastProcessedShareDataTimestamp = 2000L

    repository(shareDataTimestamp = -1L, draftText = null).load()

    assertEquals(2000L, SignalStore.misc.lastProcessedShareDataTimestamp)
  }

  private fun DraftRepository.load(): Pair<DraftRepository.ShareOrDraftData?, DraftTable.Drafts?>? {
    return getShareOrDraftData().blockingGet()
  }

  private fun repository(shareDataTimestamp: Long, draftText: String?): DraftRepository {
    return DraftRepository(
      context = mockk<Application>(relaxed = true),
      threadTable = mockk<ThreadTable>(relaxed = true),
      draftTable = draftTable,
      saveDraftsExecutor = DirectExecutor(),
      conversationArguments = conversationArgs(shareDataTimestamp, draftText)
    )
  }

  private fun conversationArgs(shareDataTimestamp: Long, draftText: String?): ConversationArgs {
    return ConversationArgs(
      recipientId = RecipientId.from(1L),
      threadId = THREAD_ID,
      draftText = draftText,
      draftMedia = null,
      draftContentType = null,
      media = null,
      stickerLocator = null,
      isBorderless = false,
      distributionType = ThreadTable.DistributionTypes.DEFAULT,
      startingPosition = -1,
      isFirstTimeInSelfCreatedGroup = false,
      isWithSearchOpen = false,
      giftBadge = null,
      shareDataTimestamp = shareDataTimestamp,
      conversationScreenType = ConversationScreenType.NORMAL
    )
  }
}
