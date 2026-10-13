/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Application
import android.content.Intent
import androidx.core.net.MailTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CommunicationActionsTest {

  @Test
  fun givenSubjectAndBody_whenBuilt_thenUriAndExtrasArePopulated() {
    val intent = CommunicationActions.buildEmailIntent("support@signal.org", "Help", "Something broke")
    val mailTo = MailTo.parse(intent.data!!)

    assertEquals(Intent.ACTION_SENDTO, intent.action)
    assertEquals("support@signal.org", mailTo.to)
    assertEquals("Help", mailTo.subject)
    assertEquals("Something broke", mailTo.body)
    assertArrayEquals(arrayOf("support@signal.org"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
    assertEquals("Help", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    assertEquals("Something broke", intent.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun givenAddressWithPlus_whenBuilt_thenPlusIsNotEscaped() {
    val intent = CommunicationActions.buildEmailIntent("john+signal@example.com", null, null)

    assertEquals("mailto:john+signal@example.com", intent.data.toString())
    assertEquals("john+signal@example.com", MailTo.parse(intent.data!!).to)
  }

  @Test
  fun givenNullSubjectAndBody_whenBuilt_thenUriHasNoQuery() {
    val intent = CommunicationActions.buildEmailIntent("support@signal.org", null, null)

    assertEquals("mailto:support@signal.org", intent.data.toString())
    assertEquals("", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    assertEquals("", intent.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun givenEmptySubjectAndBody_whenBuilt_thenUriHasNoQuery() {
    val intent = CommunicationActions.buildEmailIntent("support@signal.org", "", "")

    assertEquals("mailto:support@signal.org", intent.data.toString())
  }

  @Test
  fun givenOnlyBody_whenBuilt_thenSubjectParamIsOmitted() {
    val intent = CommunicationActions.buildEmailIntent("support@signal.org", null, "Hello")
    val mailTo = MailTo.parse(intent.data!!)

    assertFalse(intent.data.toString().contains("subject="))
    assertNull(mailTo.subject)
    assertEquals("Hello", mailTo.body)
  }

  @Test
  fun givenOnlySubject_whenBuilt_thenBodyParamIsOmitted() {
    val intent = CommunicationActions.buildEmailIntent("support@signal.org", "Hello", null)
    val mailTo = MailTo.parse(intent.data!!)

    assertFalse(intent.data.toString().contains("body="))
    assertEquals("Hello", mailTo.subject)
    assertNull(mailTo.body)
  }

  @Test
  fun givenReservedCharactersInSubjectAndBody_whenBuilt_thenTheyRoundTrip() {
    val subject = "A & B = C? #1"
    val body = "Line one\nLine two & more\nkey=value?x#y\n100% done + extra"

    val intent = CommunicationActions.buildEmailIntent("support@signal.org", subject, body)
    val mailTo = MailTo.parse(intent.data!!)

    assertEquals("support@signal.org", mailTo.to)
    assertEquals(subject, mailTo.subject)
    assertEquals(body, mailTo.body)
  }
}
