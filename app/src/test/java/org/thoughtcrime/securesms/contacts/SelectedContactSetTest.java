package org.thoughtcrime.securesms.contacts;

import org.junit.Test;
import org.thoughtcrime.securesms.recipients.RecipientId;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class SelectedContactSetTest {

  private final SelectedContactSet selectedContactSet = new SelectedContactSet();

  @Test
  public void add_without_recipient_ids() {
    SelectedContact contact1 = SelectedContact.forPhone(null, "+1-555-000-0000");
    SelectedContact contact2 = SelectedContact.forUsername(null, "@alice");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));

    assertThat(selectedContactSet.getContacts(), is(asList(contact1, contact2)));
  }

  @Test
  public void add_with_recipient_ids() {
    SelectedContact contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2 = SelectedContact.forUsername(RecipientId.from(2), "@alice");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));

    assertThat(selectedContactSet.getContacts(), is(asList(contact1, contact2)));
  }

  @Test
  public void add_with_same_recipient_id() {
    SelectedContact contact1 = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2 = SelectedContact.forUsername(RecipientId.from(1), "@alice");

    assertTrue(selectedContactSet.add(contact1));
    assertFalse(selectedContactSet.add(contact2));

    assertThat(selectedContactSet.getContacts(), is(singletonList(contact1)));
  }

  @Test
  public void remove_by_recipient_id() {
    SelectedContact contact1       = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2       = SelectedContact.forUsername(RecipientId.from(2), "@alice" );
    SelectedContact contact2Remove = SelectedContact.forUsername(RecipientId.from(2), "@alice2");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));
    assertEquals(1, selectedContactSet.remove(contact2Remove));

    assertThat(selectedContactSet.getContacts(), is(singletonList(contact1)));
  }

  @Test
  public void remove_by_number() {
    SelectedContact contact1       = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2       = SelectedContact.forUsername(RecipientId.from(2), "@alice");
    SelectedContact contact1Remove = SelectedContact.forPhone(null, "+1-555-000-0000");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));
    assertEquals(1, selectedContactSet.remove(contact1Remove));

    assertThat(selectedContactSet.getContacts(), is(singletonList(contact2)));
  }

  @Test
  public void remove_by_username() {
    SelectedContact contact1       = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2       = SelectedContact.forUsername(RecipientId.from(2), "@alice");
    SelectedContact contact2Remove = SelectedContact.forUsername(null, "@alice");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));
    assertEquals(1, selectedContactSet.remove(contact2Remove));

    assertThat(selectedContactSet.getContacts(), is(singletonList(contact1)));
  }

  @Test
  public void remove_by_recipient_id_and_username() {
    SelectedContact contact1       = SelectedContact.forPhone(RecipientId.from(1), "+1-555-000-0000");
    SelectedContact contact2       = SelectedContact.forUsername(RecipientId.from(2), "@alice");
    SelectedContact contact3       = SelectedContact.forUsername(null, "@bob");
    SelectedContact contact2Remove = SelectedContact.forUsername(RecipientId.from(1), "@bob");

    assertTrue(selectedContactSet.add(contact1));
    assertTrue(selectedContactSet.add(contact2));
    assertTrue(selectedContactSet.add(contact3));
    assertEquals(2, selectedContactSet.remove(contact2Remove));

    assertThat(selectedContactSet.getContacts(), is(singletonList(contact2)));
  }
}
