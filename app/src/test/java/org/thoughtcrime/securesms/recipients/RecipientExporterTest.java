package org.thoughtcrime.securesms.recipients;

import android.app.Application;
import android.content.Intent;
import android.provider.ContactsContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.profiles.ProfileName;

import java.util.Optional;

import static android.provider.ContactsContract.Intents.Insert.EMAIL;
import static android.provider.ContactsContract.Intents.Insert.NAME;
import static android.provider.ContactsContract.Intents.Insert.PHONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class RecipientExporterTest {

  @Test
  public void asAddContactIntent_with_phone_number() {
    Recipient recipient = givenPhoneRecipient(ProfileName.fromParts("Alice", null), "+1555123456");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Alice", intent.getStringExtra(NAME));
    assertEquals("+1555123456", intent.getStringExtra(PHONE));
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_phone_number_should_not_show_number() {
    Recipient recipient = givenPhoneRecipient(ProfileName.fromParts("Alice", null), "+1555123456", false);

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Alice", intent.getStringExtra(NAME));
    assertNull(intent.getStringExtra(PHONE));
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_email() {
    Recipient recipient = givenEmailRecipient(ProfileName.fromParts("Bob", null), "bob@signal.org");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Bob", intent.getStringExtra(NAME));
    assertEquals("bob@signal.org", intent.getStringExtra(EMAIL));
    assertNull(intent.getStringExtra(PHONE));
  }


  private Recipient givenPhoneRecipient(ProfileName profileName, String phone) {
    return givenPhoneRecipient(profileName, phone, true);
  }

  private Recipient givenPhoneRecipient(ProfileName profileName, String phone, boolean shouldShowPhoneNumber) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireE164()).thenReturn(phone);
    when(recipient.getE164()).thenAnswer(i -> Optional.of(phone));
    when(recipient.getEmail()).thenAnswer(i -> Optional.empty());
    when(recipient.getShouldShowE164()).thenAnswer(i -> shouldShowPhoneNumber);

    return recipient;
  }

  private Recipient givenEmailRecipient(ProfileName profileName, String email) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireEmail()).thenReturn(email);
    when(recipient.getEmail()).thenAnswer(i -> Optional.of(email));
    when(recipient.getE164()).thenAnswer(i -> Optional.empty());

    return recipient;
  }
}
