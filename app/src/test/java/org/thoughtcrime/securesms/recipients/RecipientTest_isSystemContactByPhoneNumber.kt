/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberDiscoverableState
import org.thoughtcrime.securesms.database.RecipientTable.PhoneNumberSharingState
import java.util.UUID

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTest_isSystemContactByPhoneNumber {

  @Test
  fun `a linked contact holding the number counts`() {
    val recipient = recipient()

    assertTrue(recipient.isSystemContactByPhoneNumber)
    assertTrue(recipient.shouldShowE164)
  }

  @Test
  fun `an unlinked recipient does not count`() {
    val recipient = recipient(contactUri = null)

    assertFalse(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
    assertFalse(recipient.shouldShowE164)
  }

  @Test
  fun `a linked contact without the number keeps the recipient known but hides the number`() {
    val recipient = recipient(systemContactHasNumber = false)

    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
    assertFalse(recipient.shouldShowE164)
  }

  @Test
  fun `an undiscoverable recipient who does not share their number does not count`() {
    val recipient = recipient(phoneNumberDiscoverable = PhoneNumberDiscoverableState.NOT_DISCOVERABLE)

    assertTrue(recipient.isSystemContact)
    assertFalse(recipient.isSystemContactByPhoneNumber)
    assertFalse(recipient.shouldShowE164)
  }

  @Test
  fun `an undiscoverable recipient who shares their number counts`() {
    val recipient = recipient(
      phoneNumberDiscoverable = PhoneNumberDiscoverableState.NOT_DISCOVERABLE,
      phoneNumberSharing = PhoneNumberSharingState.ENABLED
    )

    assertTrue(recipient.isSystemContactByPhoneNumber)
    assertTrue(recipient.shouldShowE164)
  }

  @Test
  fun `an unknown discoverability counts`() {
    val recipient = recipient(phoneNumberDiscoverable = PhoneNumberDiscoverableState.UNKNOWN)

    assertTrue(recipient.isSystemContactByPhoneNumber)
  }

  private fun recipient(
    contactUri: Uri? = Uri.parse("content://com.android.contacts/contacts/lookup/0r1-ABC/1"),
    systemContactHasNumber: Boolean = true,
    phoneNumberDiscoverable: PhoneNumberDiscoverableState = PhoneNumberDiscoverableState.DISCOVERABLE,
    phoneNumberSharing: PhoneNumberSharingState = PhoneNumberSharingState.DISABLED
  ): Recipient {
    return Recipient(
      id = RecipientId.from(1),
      isResolving = false,
      aciValue = ACI.from(UUID.randomUUID()),
      e164Value = "+15555550101",
      contactUri = contactUri,
      systemContactHasNumber = systemContactHasNumber,
      phoneNumberDiscoverable = phoneNumberDiscoverable,
      phoneNumberSharing = phoneNumberSharing
    )
  }
}
