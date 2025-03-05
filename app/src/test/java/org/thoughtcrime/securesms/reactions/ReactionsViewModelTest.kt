/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.reactions

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.MessageId
import java.util.NoSuchElementException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ReactionsViewModelTest {

  private val testScheduler = TestScheduler()
  private val repository = mockk<ReactionsRepository>()

  @Before
  fun setUp() {
    RxJavaPlugins.setInitIoSchedulerHandler { testScheduler }
    RxJavaPlugins.setIoSchedulerHandler { testScheduler }
  }

  @After
  fun tearDown() {
    RxJavaPlugins.reset()
  }

  @Test
  fun `Given a message Id, when I removeReactionEmoji, then I expect Success`() {
    // GIVEN
    val messageId = MessageId(0)
    val testSubject = ReactionsViewModel(
      repository,
      messageId
    )
    every { repository.sendReactionRemoval(any()) } returns Completable.complete()

    // WHEN
    val result = testSubject.removeReactionEmoji()

    // THEN
    verify(exactly = 1) { repository.sendReactionRemoval(any()) }
    result.test().assertNoErrors()
  }

  @Test
  fun `Given an invalid message Id, when I removeReactionEmoji, then I expect Failure`() {
    // GIVEN
    val messageId = MessageId(0)
    val testSubject = ReactionsViewModel(
      repository,
      messageId
    )
    every { repository.sendReactionRemoval(any()) } returns Completable.error(NoSuchElementException())

    // WHEN
    val result = testSubject.removeReactionEmoji()
    testScheduler.triggerActions()

    // THEN
    verify(exactly = 1) { repository.sendReactionRemoval(any()) }
    result.test().assertError(NoSuchElementException::class.java)
  }


}