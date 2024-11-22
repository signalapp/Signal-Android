package org.thoughtcrime.securesms.recipients

import android.app.Application
import android.content.Intent
import android.provider.ContactsContract
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.profiles.ProfileName
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientExporterTest {
  @Test
  fun asAddContactIntent_with_phone_number() {
    val recipient = givenPhoneRecipient(
      profileName = ProfileName.fromParts("Alice", null),
      phone = "+1555123456"
    )

    val intent = RecipientExporter.export(recipient).asAddContactIntent()

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.type)
    assertEquals("Alice", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
    assertEquals("+1555123456", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
    assertNull(intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL))
  }

  @Test
  fun asAddContactIntent_with_phone_number_should_not_show_number() {
    val recipient = givenPhoneRecipient(
      profileName = ProfileName.fromParts("Alice", null),
      phone = "+1555123456",
      shouldShowPhoneNumber = false
    )

    val intent = RecipientExporter.export(recipient).asAddContactIntent()

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.type)
    assertEquals("Alice", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
    assertNull(intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
    assertNull(intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL))
  }

  @Test
  fun asAddContactIntent_with_email() {
    val recipient = givenEmailRecipient(
      profileName = ProfileName.fromParts("Bob", null),
      email = "bob@signal.org"
    )

    val intent = RecipientExporter.export(recipient).asAddContactIntent()

    assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
    assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.type)
    assertEquals("Bob", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
    assertEquals("bob@signal.org", intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL))
    assertNull(intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
  }

  private fun givenPhoneRecipient(profileName: ProfileName, phone: String, shouldShowPhoneNumber: Boolean = true): Recipient {
    val recipient = mockk<Recipient>()
    every { recipient.profileName } returns profileName

    every { recipient.requireE164() } returns phone
    every { recipient.e164 } returns Optional.of(phone)
    every { recipient.email } returns Optional.empty()
    every { recipient.shouldShowE164 } returns shouldShowPhoneNumber

    return recipient
  }

  private fun givenEmailRecipient(profileName: ProfileName, email: String): Recipient {
    val recipient = mockk<Recipient>()
    every { recipient.profileName } returns profileName

    every { recipient.requireEmail() } returns email
    every { recipient.email } returns Optional.of(email)
    every { recipient.e164 } returns Optional.empty()

    return recipient
  }
}
