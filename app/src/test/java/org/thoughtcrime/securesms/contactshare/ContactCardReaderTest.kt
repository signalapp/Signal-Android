/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.io.ByteArrayInputStream

/**
 * Covers reading a card off a vcard uri. A malformed or empty vcard has to come back as nothing rather
 * than taking the whole read down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactCardReaderTest {

  private val reader = ContactCardReader(ApplicationProvider.getApplicationContext())

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    mockkStatic(PartAuthority::class)

    // AppDependencies is suite scoped and MockAppDependenciesRule clears its mocks between classes, so
    // the relaxed default cannot be relied on here. The authority deliberately does not match the uri
    // under test, which is what stops the reader trying to delete it as a blob.
    every { AppDependencies.blobs.authority } returns "not-a-blob-authority"
  }

  @After
  fun tearDown() {
    unmockkStatic(PartAuthority::class)
  }

  @Test
  fun `an empty vcard is skipped rather than throwing`() {
    givenVcard("")

    assertThat(reader.readVCard(VCARD_URI)).isNull()
  }

  @Test
  fun `an unparseable vcard is skipped rather than throwing`() {
    givenVcard("this is not a vcard at all")

    assertThat(reader.readVCard(VCARD_URI)).isNull()
  }

  @Test
  fun `a vcard with a name is read into a card`() {
    givenVcard(
      """
      BEGIN:VCARD
      VERSION:3.0
      N:Hall;Paige;;;
      FN:Paige Hall
      TEL;TYPE=CELL:+15105550101
      END:VCARD
      """.trimIndent()
    )

    val contact = reader.readVCard(VCARD_URI)

    assertThat(contact).isNotNull()
    assertThat(ContactUtil.getDisplayName(contact!!)).isEqualTo("Paige Hall")
    assertThat(contact.phoneNumbers.first().number).isEqualTo("+15105550101")
  }

  @Test
  fun `vcard types are read off the raw parameter`() {
    givenVcard(
      """
      BEGIN:VCARD
      VERSION:3.0
      FN:Paige Hall
      TEL;TYPE=WORK:+15105550101
      TEL;TYPE=X-ASSISTANT:+15105550102
      EMAIL;TYPE=HOME:paige@example.com
      END:VCARD
      """.trimIndent()
    )

    val contact = reader.readVCard(VCARD_URI)!!

    assertThat(contact.phoneNumbers[0].type).isEqualTo(Contact.Phone.Type.WORK)
    assertThat(contact.phoneNumbers[1].type).isEqualTo(Contact.Phone.Type.CUSTOM)
    assertThat(contact.phoneNumbers[1].label).isEqualTo("X-ASSISTANT")
    assertThat(contact.emails[0].type).isEqualTo(Contact.Email.Type.HOME)
  }

  private fun givenVcard(body: String) {
    every { PartAuthority.getAttachmentStream(any(), any()) } returns ByteArrayInputStream(body.toByteArray())
  }

  companion object {
    private val VCARD_URI = "content://org.thoughtcrime.securesms/vcard/1".toUri()
  }
}
