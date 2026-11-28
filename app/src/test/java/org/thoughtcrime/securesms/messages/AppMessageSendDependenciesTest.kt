/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AppMessageSendDependenciesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Before
  fun setUp() {
    mockkStatic(SealedSenderAccessUtil::class)
    every { SealedSenderAccessUtil.getAccessMapFor(any(), any()) } answers { firstArg<List<Recipient>>().associate { it.id to Optional.empty() } }
  }

  @After
  fun tearDown() {
    unmockkStatic(SealedSenderAccessUtil::class)
  }

  @Test
  fun `toGroupSendRecipients leaves out recipients that can't be sent to`() {
    val alice = recipients.createRecipient("Alice")
    val unregistered = recipients.createRecipient("Bob").also { SignalDatabase.recipients.markUnregistered(it) }
    val noServiceId = SignalDatabase.recipients.getOrInsertFromE164("+15555550123")

    val result = AppMessageSendDependencies.toGroupSendRecipients(listOf(alice, unregistered, noServiceId).map { Recipient.resolved(it) } + Recipient.UNKNOWN)

    assertThat(result.map { it.id }).containsExactly(alice.toLong())
  }
}
