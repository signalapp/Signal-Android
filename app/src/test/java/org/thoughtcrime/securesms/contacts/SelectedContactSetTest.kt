package org.thoughtcrime.securesms.contacts

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.recipients.RecipientId

class SelectedContactSetTest {
  private val selectedContactSet = SelectedContactSet()

  @Test
  fun add_without_recipient_ids() {
    val contact1 = SelectedContact.forPhone(null, "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(null, "@alice")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()

    assertThat(selectedContactSet.contacts).containsExactly(contact1, contact2)
  }

  @Test
  fun add_with_recipient_ids() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()

    assertThat(selectedContactSet.contacts).containsExactly(contact1, contact2)
  }

  @Test
  fun add_with_same_recipient_id() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(1), "@alice")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isFalse()

    assertThat(selectedContactSet.contacts).containsExactly(contact1)
  }

  @Test
  fun remove_by_recipient_id() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice")
    val contact2Remove = SelectedContact.forUsername(RecipientId.from(2), "@alice2")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()
    assertThat(selectedContactSet.remove(contact2Remove)).isEqualTo(1)

    assertThat(selectedContactSet.contacts).containsExactly(contact1)
  }

  @Test
  fun remove_by_number() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice")
    val contact1Remove = SelectedContact.forPhone(null, "+1-555-000-0000")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()
    assertThat(selectedContactSet.remove(contact1Remove).toLong()).isEqualTo(1)

    assertThat(selectedContactSet.contacts).containsExactly(contact2)
  }

  @Test
  fun remove_by_username() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice")
    val contact2Remove = SelectedContact.forUsername(null, "@alice")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()
    assertThat(selectedContactSet.remove(contact2Remove)).isEqualTo(1)

    assertThat(selectedContactSet.contacts).containsExactly(contact1)
  }

  @Test
  fun remove_by_recipient_id_and_username() {
    val contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000")
    val contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice")
    val contact3 = SelectedContact.forUsername(null, "@bob")
    val contact2Remove = SelectedContact.forUsername(RecipientId.from(1), "@bob")

    assertThat(selectedContactSet.add(contact1)).isTrue()
    assertThat(selectedContactSet.add(contact2)).isTrue()
    assertThat(selectedContactSet.add(contact3)).isTrue()
    assertThat(selectedContactSet.remove(contact2Remove)).isEqualTo(2)

    assertThat(selectedContactSet.contacts).containsExactly(contact2)
  }
}
