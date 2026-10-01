/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Application
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexType
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsEvent
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameEvent
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameResult
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactAction
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactEvent
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactAction
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactEvent
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * EventDrivenViewModel logs every event so try to enforce clean logs via reflection.
 *
 * Robolectric only so that a [Uri] can be built for the [SharedContactSource] sample.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareContactPiiLoggingTest {

  @Test
  fun `no event or action renders a value the user would consider private`() {
    val offenders = HIERARCHIES
      .flatMap { membersOf(it) }
      .filterNot { it in LOGS_ITS_VALUE }
      .mapNotNull { member ->
        val rendered = instantiate(member).toString()
        val leaked = SECRETS.filter { rendered.contains(it) }

        if (leaked.isEmpty()) null else "${member.qualifiedName} -> $rendered"
      }

    if (offenders.isNotEmpty()) {
      throw AssertionError(
        "These payloads render private values and are logged verbatim. Override toString to report " +
          "presence rather than content, or add them to LOGS_ITS_VALUE if the value is not private:\n" +
          offenders.joinToString("\n")
      )
    }
  }

  @Test
  fun `every hierarchy actually produced members to check`() {
    for (hierarchy in HIERARCHIES) {
      if (membersOf(hierarchy).isEmpty()) {
        throw AssertionError("${hierarchy.simpleName} produced no members, so it is not really being covered.")
      }
    }
  }

  /** Flattens a sealed hierarchy, including any nested sealed layers. */
  private fun membersOf(hierarchy: KClass<*>): List<KClass<*>> {
    return hierarchy.sealedSubclasses.flatMap { subclass ->
      if (subclass.isSealed) membersOf(subclass) else listOf(subclass)
    }
  }

  private fun instantiate(member: KClass<*>): Any {
    member.objectInstance?.let { return it }

    val constructor = member.primaryConstructor
      ?: throw AssertionError("${member.qualifiedName} has no primary constructor, so this test cannot build it.")

    return constructor.call(*constructor.parameters.map { sampleFor(it.type, member) }.toTypedArray())
  }

  /**
   * Every value handed in is one a user would not want in a log, so anything echoed back is a leak.
   * An unknown type fails rather than being skipped, which is what keeps new payloads covered.
   */
  private fun sampleFor(type: KType, member: KClass<*>): Any {
    val classifier = type.classifier as? KClass<*>

    return when {
      classifier == String::class -> SECRET_TEXT
      classifier == ContactNameParts::class -> secretNameParts()
      classifier == Contact::class -> secretContact()
      classifier == ContactIndexRecord::class -> secretContactRow()
      classifier == RecipientId::class -> RecipientId.from(1L)
      classifier == SharedContactSource::class -> secretSource()
      classifier == Uri::class -> secretContactUri()
      classifier?.java?.isEnum == true -> classifier.java.enumConstants.first()
      else -> throw AssertionError(
        "No sample value for ${classifier?.simpleName} on ${member.qualifiedName}. Add one to " +
          "sampleFor so this payload is actually checked."
      )
    }
  }

  private fun secretNameParts(): ContactNameParts {
    return ContactNameParts(
      prefix = SECRET_TEXT,
      givenName = SECRET_TEXT,
      middleName = SECRET_TEXT,
      familyName = SECRET_TEXT,
      suffix = SECRET_TEXT,
      organization = SECRET_TEXT
    )
  }

  private fun secretContactRow(): ContactIndexRecord {
    return ContactIndexRecord(
      position = 1,
      type = ContactIndexType.BOTH,
      section = SECRET_TEXT,
      displayName = SECRET_TEXT,
      recipientId = RecipientId.from(1L),
      lookupKey = SECRET_TEXT,
      contactId = 1L,
      hasPersonalName = true,
      hasPhoto = false
    )
  }

  /** A lookup key can carry the contact's own name, so the uri counts as private. */
  private fun secretSource(): SharedContactSource {
    return SharedContactSource.AddressBook(secretContactUri())
  }

  /** A lookup key is built out of the contact's own details, so the uri names the person. */
  private fun secretContactUri(): Uri {
    return Uri.parse("content://com.android.contacts/contacts/lookup/$SECRET_TEXT/1")
  }

  private fun secretContact(): Contact {
    return Contact(
      Contact.Name(SECRET_TEXT, SECRET_TEXT, SECRET_TEXT, SECRET_TEXT, SECRET_TEXT, SECRET_TEXT),
      SECRET_TEXT,
      listOf(Contact.Phone(SECRET_NUMBER, Contact.Phone.Type.MOBILE, SECRET_TEXT)),
      listOf(Contact.Email(SECRET_EMAIL, Contact.Email.Type.HOME, SECRET_TEXT)),
      listOf(Contact.PostalAddress(Contact.PostalAddress.Type.HOME, SECRET_TEXT, SECRET_TEXT, null, null, null, null, null, null)),
      null,
      null,
      Contact.SignalNickname(SECRET_TEXT, SECRET_TEXT),
      SECRET_TEXT
    )
  }

  companion object {
    private const val SECRET_TEXT = "Paige Hall"
    private const val SECRET_NUMBER = "+15105550101"
    private const val SECRET_EMAIL = "paigehall@example.com"

    private val SECRETS = listOf(SECRET_TEXT, SECRET_NUMBER, SECRET_EMAIL)

    private val HIERARCHIES = listOf(
      ShareContactEvent::class,
      ShareContactAction::class,
      SharedContactDetailsEvent::class,
      SharedContactDetailsAction::class,
      EditContactNameEvent::class,
      EditContactNameResult::class,
      SelectContactEvent::class,
      SelectContactAction::class
    )

    /** Payloads whose only string is an internal identifier, so logging it verbatim is useful and safe. */
    private val LOGS_ITS_VALUE = setOf(
      ShareContactEvent.DetailToggled::class, // positional detail id, e.g. phone:0
      ShareContactEvent.PhotoSelected::class, // address-book or signal-profile
      SharedContactDetailsEvent.DetailPressed::class // positional detail id
    )
  }
}
