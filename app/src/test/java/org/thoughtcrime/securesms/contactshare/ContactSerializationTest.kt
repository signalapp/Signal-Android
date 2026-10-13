/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The serialized form is what sits in the shared_contacts column, so it has to stay readable across
 * versions in both directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactSerializationTest {

  @Test
  fun `round trips every field`() {
    val contact = Contact(
      Contact.Name("Paige", "Hall", "Dr.", "Jr.", "Quinn", "Pea"),
      "Pacific Plumbing",
      listOf(Contact.Phone("+15105550101", Contact.Phone.Type.MOBILE, null)),
      listOf(Contact.Email("paige@example.com", Contact.Email.Type.WORK, null)),
      listOf(Contact.PostalAddress(Contact.PostalAddress.Type.HOME, null, "1 Main St", null, null, "Oakland", "CA", "94612", "US")),
      null,
      ACI_STRING,
      Contact.SignalNickname("Pea", "H"),
      "Met at the conference"
    )

    val restored = Contact.deserialize(contact.serialize())

    assertThat(restored.name.givenName).isEqualTo("Paige")
    assertThat(restored.organization).isEqualTo("Pacific Plumbing")
    assertThat(restored.phoneNumbers.single().number).isEqualTo("+15105550101")
    assertThat(restored.emails.single().email).isEqualTo("paige@example.com")
    assertThat(restored.postalAddresses.single().city).isEqualTo("Oakland")
    assertThat(restored.aci).isEqualTo(ACI_STRING)
    assertThat(restored.nickname?.given).isEqualTo("Pea")
    assertThat(restored.nickname?.family).isEqualTo("H")
    assertThat(restored.note).isEqualTo("Met at the conference")
  }

  @Test
  fun `uses the stored property names`() {
    val json = JSONObject(
      Contact(
        Contact.Name("Paige", null, null, null, null, null),
        "Pacific Plumbing",
        listOf(Contact.Phone("+15105550101", Contact.Phone.Type.MOBILE, "cell")),
        emptyList(),
        emptyList(),
        null,
        ACI_STRING,
        Contact.SignalNickname("Pea", null),
        "a note"
      ).serialize()
    )

    assertThat(json.getJSONObject("name").getString("givenName")).isEqualTo("Paige")
    assertThat(json.getString("organization")).isEqualTo("Pacific Plumbing")
    assertThat(json.getJSONArray("phoneNumbers").getJSONObject(0).getString("number")).isEqualTo("+15105550101")
    assertThat(json.has("emails")).isTrue()
    assertThat(json.has("postalAddresses")).isTrue()
    assertThat(json.getString("aci")).isEqualTo(ACI_STRING)
    assertThat(json.getJSONObject("nickname").getString("given")).isEqualTo("Pea")
    assertThat(json.getString("note")).isEqualTo("a note")
  }

  /** The selection flag is transient, so it must never reach the column. */
  @Test
  fun `does not serialize selection state`() {
    val json = JSONObject(
      Contact(
        Contact.Name("Paige", null, null, null, null, null),
        null,
        listOf(Contact.Phone("+15105550101", Contact.Phone.Type.MOBILE, null)),
        emptyList(),
        emptyList(),
        null
      ).serialize()
    )

    assertThat(json.getJSONArray("phoneNumbers").getJSONObject(0).has("selected")).isEqualTo(false)
  }

  /** A row written before the ACI work has none of the new keys and must still read back. */
  @Test
  fun `reads a card written before the new fields existed`() {
    val legacy = """
      {
        "name": {"givenName":"Paige","familyName":"Hall","prefix":null,"suffix":null,"middleName":null,"nickname":null,"empty":false},
        "organization": "Pacific Plumbing",
        "phoneNumbers": [{"number":"+15105550101","type":"MOBILE","label":null}],
        "emails": [],
        "postalAddresses": [],
        "avatar": null
      }
    """.trimIndent()

    val contact = Contact.deserialize(legacy)

    assertThat(contact.name.givenName).isEqualTo("Paige")
    assertThat(contact.phoneNumbers.single().type).isEqualTo(Contact.Phone.Type.MOBILE)
    assertThat(contact.aci).isNull()
    assertThat(contact.nickname).isNull()
    assertThat(contact.note).isNull()
  }

  /** A null name has always been allowed for a card whose only name is its company. */
  @Test
  fun `reads a card with a null name`() {
    val legacy = """
      {
        "name": null,
        "organization": "Pacific Plumbing",
        "phoneNumbers": [],
        "emails": [],
        "postalAddresses": [],
        "avatar": null
      }
    """.trimIndent()

    assertThat(Contact.deserialize(legacy).name.isEmpty).isTrue()
  }

  companion object {
    private const val ACI_STRING = "3b2c8e2a-1f4d-4a5e-9c7b-0d1e2f3a4b5c"
  }
}
