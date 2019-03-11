package org.thoughtcrime.securesms.recipients;

import android.app.Application;
import android.content.Intent;
import android.provider.ContactsContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.database.Address;

import static org.assertj.core.api.Assertions.*;

import static android.provider.ContactsContract.Intents.Insert.NAME;
import static android.provider.ContactsContract.Intents.Insert.EMAIL;
import static android.provider.ContactsContract.Intents.Insert.PHONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class RecipientExporterTest {

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
