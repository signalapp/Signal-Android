/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_getRecordsForProfileFetch {

  @get:Rule
  val recipients = RecipientTestRule()

  private val now = System.currentTimeMillis().milliseconds

  @Test
  fun `registered recipient is fetched once outside the debounce window`() {
    val recent = recipients.createRecipient("Alice")
    val stale = recipients.createRecipient("Bob")
    markFetched(recent, 1.minutes)
    markFetched(stale, 10.minutes)

    assertThat(fetch(recent, stale)).containsExactlyInAnyOrder(stale)
  }

  @Test
  fun `unregistered recipient uses the longer debounce window`() {
    val recent = recipients.createRecipient("Alice")
    val stale = recipients.createRecipient("Bob")
    SignalDatabase.recipients.markUnregistered(listOf(recent, stale))
    markFetched(recent, 10.minutes)
    markFetched(stale, 7.hours)

    assertThat(fetch(recent, stale)).containsExactlyInAnyOrder(stale)
  }

  @Test
  fun `no thresholds skips debouncing for all recipients`() {
    val registered = recipients.createRecipient("Alice")
    val unregistered = recipients.createRecipient("Bob")
    SignalDatabase.recipients.markUnregistered(unregistered)
    markFetched(registered, 1.minutes)
    markFetched(unregistered, 1.minutes)

    val ids = SignalDatabase.recipients.getRecordsForProfileFetch(listOf(registered, unregistered), null, null).map { it.id }

    assertThat(ids).containsExactlyInAnyOrder(registered, unregistered)
  }

  private fun markFetched(id: RecipientId, ago: Duration) {
    SignalDatabase.recipients.markProfilesFetched(listOf(id), (now - ago).inWholeMilliseconds)
  }

  private fun fetch(vararg ids: RecipientId): List<RecipientId> {
    return SignalDatabase.recipients
      .getRecordsForProfileFetch(ids.toList(), now - 5.minutes, now - 6.hours)
      .map { it.id }
  }
}
