package org.thoughtcrime.securesms.recipients;

import android.content.Intent;
import android.provider.ContactsContract;

import junit.framework.TestCase;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.session.libsession.utilities.Address;

import static android.provider.ContactsContract.Intents.Insert.EMAIL;
import static android.provider.ContactsContract.Intents.Insert.NAME;
import static android.provider.ContactsContract.Intents.Insert.PHONE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//FIXME AC: This test group is outdated.
@Ignore("This test group uses outdated instrumentation and needs a migration to modern tools.")
@RunWith(MockitoJUnitRunner.class)
public final class RecipientExporterTest extends TestCase {

  @Test
  public void asAddContactIntent_with_phone_number() {
    Recipient recipient = givenRecipient("Alice", givenPhoneNumber("+1555123456"));

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Alice", intent.getStringExtra(NAME));
    assertEquals("+1555123456", intent.getStringExtra(PHONE));
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_email() {
    Recipient recipient = givenRecipient("Bob", givenEmail("bob@signal.org"));

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Bob", intent.getStringExtra(NAME));
    assertEquals("bob@signal.org", intent.getStringExtra(EMAIL));
    assertNull(intent.getStringExtra(PHONE));
  }

  @Test
  public void asAddContactIntent_with_neither_email_nor_phone() {
    RecipientExporter exporter = RecipientExporter.export(givenRecipient("Bob", mock(Address.class)));

    assertThatThrownBy(exporter::asAddContactIntent).isExactlyInstanceOf(RuntimeException.class)
                                                    .hasMessage("Cannot export Recipient with neither phone nor email");
  }

  private Recipient givenRecipient(String profileName, Address address) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);
    when(recipient.getAddress()).thenReturn(address);
    return recipient;
  }

  private Address givenPhoneNumber(String phoneNumber) {
    Address address = mock(Address.class);
    when(address.isPhone()).thenReturn(true);
    when(address.toPhoneString()).thenReturn(phoneNumber);
    when(address.toEmailString()).thenThrow(new RuntimeException());
    return address;
  }

  private Address givenEmail(String email) {
    Address address = mock(Address.class);
    when(address.isEmail()).thenReturn(true);
    when(address.toEmailString()).thenReturn(email);
    when(address.toPhoneString()).thenThrow(new RuntimeException());
    return address;
  }
}
