package org.thoughtcrime.securesms.recipients;

import android.app.Application;
import android.content.Intent;
import android.provider.ContactsContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.whispersystems.libsignal.util.guava.Optional;

import static android.provider.ContactsContract.Intents.Insert.NAME;
import static android.provider.ContactsContract.Intents.Insert.EMAIL;
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
    Recipient recipient = givenPhoneRecipient("Alice", "+1555123456");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Alice", intent.getStringExtra(NAME));
    assertEquals("+1555123456", intent.getStringExtra(PHONE));
    assertNull(intent.getStringExtra(EMAIL));
  }

  @Test
  public void asAddContactIntent_with_email() {
    Recipient recipient = givenEmailRecipient("Bob", "bob@signal.org");

    Intent intent = RecipientExporter.export(recipient).asAddContactIntent();

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction());
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.getType());
    assertEquals("Bob", intent.getStringExtra(NAME));
    assertEquals("bob@signal.org", intent.getStringExtra(EMAIL));
    assertNull(intent.getStringExtra(PHONE));
  }

  private Recipient givenPhoneRecipient(String profileName, String phone) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireE164()).thenReturn(phone);
    when(recipient.getE164()).thenAnswer(i -> Optional.of(phone));
    when(recipient.getEmail()).thenAnswer(i -> Optional.absent());

    return recipient;
  }

  private Recipient givenEmailRecipient(String profileName, String email) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getProfileName()).thenReturn(profileName);

    when(recipient.requireEmail()).thenReturn(email);
    when(recipient.getEmail()).thenAnswer(i -> Optional.of(email));
    when(recipient.getE164()).thenAnswer(i -> Optional.absent());

    return recipient;
  }
}
