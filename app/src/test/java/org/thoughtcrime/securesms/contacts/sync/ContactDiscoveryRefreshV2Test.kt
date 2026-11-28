/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.network.NetworkResult
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException
import org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactDiscoveryRefreshV2Test {

  @get:Rule
  val recipients = RecipientTestRule()

  private val context: Application = ApplicationProvider.getApplicationContext()

  private val cds = FakeCdsServer()
  private val systemContacts = FakeSystemContacts()
  private lateinit var cdsState: FakeCdsStoreState

  @Before
  fun setUp() {
    cdsState = FakeCdsStoreState(recipients.signalStore.misc).also { it.install() }
    cds.install()
    systemContacts.install()

    every { RemoteConfig.cdsHardLimit } returns 50_000
  }

  @After
  fun tearDown() {
    cds.uninstall()
    systemContacts.uninstall()
  }

  @Test
  fun `refreshAll sends recipient and system numbers as new when there is no token`() {
    SignalDatabase.recipients.getOrInsertFromE164(ALICE)
    systemContacts.add(BOB, "Bob")

    ContactDiscoveryRefreshV2.refreshAll(context)

    val request = cds.requests.single()
    assertTrue(request.previousE164s.isEmpty())
    assertNull(request.token)
    assertTrue(request.newE164s.containsAll(setOf(ALICE, BOB, recipients.selfE164)))
  }

  @Test
  fun `refreshAll saves the token and every queried number to the cds table`() {
    systemContacts.add(ALICE, "Alice")
    systemContacts.add(BOB, "Bob")

    ContactDiscoveryRefreshV2.refreshAll(context)

    assertNotNull(cdsState.cdsToken)
    assertTrue(SignalDatabase.cds.getAllE164s().containsAll(setOf(ALICE, BOB)))
  }

  @Test
  fun `refreshAll only sends unseen numbers as new once a token exists`() {
    systemContacts.add(ALICE, "Alice")
    ContactDiscoveryRefreshV2.refreshAll(context)
    val firstToken = cdsState.cdsToken

    systemContacts.add(BOB, "Bob")
    ContactDiscoveryRefreshV2.refreshAll(context)

    val request = cds.requests[1]
    assertArrayEquals(firstToken, request.token)
    assertTrue(ALICE in request.previousE164s)
    assertEquals(setOf(BOB), request.newE164s)
  }

  @Test
  fun `refreshAll returns registered ids and only creates recipients for registered numbers`() {
    cds.register(BOB)
    systemContacts.add(ALICE, "Alice")
    systemContacts.add(BOB, "Bob")

    val result = ContactDiscoveryRefreshV2.refreshAll(context)

    val bob = SignalDatabase.recipients.getByE164(BOB).get()
    assertTrue(bob in result.registeredIds)
    assertFalse(SignalDatabase.recipients.getByE164(ALICE).isPresent)
  }

  @Test
  fun `refreshAll marks a recipient missing from cds as unregistered when there is no communication history`() {
    val bob = SignalDatabase.recipients.getOrInsertFromE164(BOB)
    SignalDatabase.recipients.markRegistered(bob, cds.register(BOB).aci!!)
    cds.registered.clear()

    ContactDiscoveryRefreshV2.refreshAll(context)

    assertEquals(RegisteredState.NOT_REGISTERED, SignalDatabase.recipients.getRecord(bob).registered)
  }

  @Test
  fun `refreshAll keeps a recipient missing from cds registered when there is an active thread`() {
    val bob = SignalDatabase.recipients.getOrInsertFromE164(BOB)
    SignalDatabase.recipients.markRegistered(bob, cds.register(BOB).aci!!)
    recipients.insertIncomingMessage(bob)
    cds.registered.clear()

    ContactDiscoveryRefreshV2.refreshAll(context)

    assertEquals(RegisteredState.REGISTERED, SignalDatabase.recipients.getRecord(bob).registered)
  }

  @Test
  fun `refreshAll never marks self as unregistered`() {
    ContactDiscoveryRefreshV2.refreshAll(context)

    assertEquals(RegisteredState.REGISTERED, SignalDatabase.recipients.getRecord(recipients.self).registered)
  }

  @Test
  fun `refresh with a small set of recipients does a one-off lookup without saving state`() {
    cds.register(BOB)
    val bob = SignalDatabase.recipients.getOrInsertFromE164(BOB)

    val result = ContactDiscoveryRefreshV2.refresh(context, listOf(Recipient.resolved(bob)))

    assertEquals(setOf(bob), result.registeredIds)
    assertEquals(setOf(BOB), cds.requests.single().newE164s)
    assertNull(cdsState.cdsToken)
    assertTrue(SignalDatabase.cds.getAllE164s().isEmpty())
  }

  @Test
  fun `refresh with a large set of recipients falls back to a full refresh`() {
    val ids = (1..ContactDiscoveryRefreshV2.MAXIMUM_ONE_OFF_REQUEST_SIZE + 1).map { SignalDatabase.recipients.getOrInsertFromE164("+1555555%04d".format(it)) }

    ContactDiscoveryRefreshV2.refresh(context, Recipient.resolvedList(ids))

    assertNotNull(cdsState.cdsToken)
    assertTrue(cds.requests.single().newE164s.contains(recipients.selfE164))
  }

  @Test
  fun `an invalid token clears the token and the cds table`() {
    systemContacts.add(ALICE, "Alice")
    ContactDiscoveryRefreshV2.refreshAll(context)

    cds.nextFailure = NetworkResult.StatusCodeError(CdsiInvalidTokenException())
    assertThrows(CdsiInvalidTokenException::class.java) { ContactDiscoveryRefreshV2.refreshAll(context) }

    assertNull(cdsState.cdsToken)
    assertTrue(SignalDatabase.cds.getAllE164s().isEmpty())
  }

  @Test
  fun `resource exhaustion records when we are blocked until`() {
    systemContacts.add(ALICE, "Alice")

    cds.nextFailure = NetworkResult.StatusCodeError(CdsiResourceExhaustedException(60))
    assertThrows(CdsiResourceExhaustedException::class.java) { ContactDiscoveryRefreshV2.refreshAll(context) }

    assertTrue(cdsState.cdsBlockedUntil > System.currentTimeMillis())
  }

  @Test
  fun `exceeding the hard limit marks cds permanently blocked without making a request`() {
    every { RemoteConfig.cdsHardLimit } returns 1
    systemContacts.add(ALICE, "Alice")
    systemContacts.add(BOB, "Bob")

    assertThrows(IOException::class.java) { ContactDiscoveryRefreshV2.refreshAll(context) }

    assertTrue(cdsState.permanentlyBlocked)
    assertTrue(cds.requests.isEmpty())
  }

  companion object {
    private const val ALICE = "+15555550101"
    private const val BOB = "+15555550102"
  }
}
