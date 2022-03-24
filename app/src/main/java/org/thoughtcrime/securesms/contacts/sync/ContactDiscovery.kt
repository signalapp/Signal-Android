package org.thoughtcrime.securesms.contacts.sync

import android.accounts.Account
import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.contacts.ContactLinkConfiguration
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException

/**
 * Methods for discovering which users are registered and marking them as such in the database.
 */
object ContactDiscovery {

  private const val MESSAGE_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
  private const val CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refreshAll(context: Context, notifyOfNewUsers: Boolean) {
    DirectoryHelper.refreshAll(context, notifyOfNewUsers)
  }

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipients: List<Recipient>, notifyOfNewUsers: Boolean) {
    return DirectoryHelper.refresh(context, recipients, notifyOfNewUsers)
  }

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipient: Recipient, notifyOfNewUsers: Boolean): RecipientDatabase.RegisteredState {
    return DirectoryHelper.refresh(context, recipient, notifyOfNewUsers)
  }

  @JvmStatic
  @WorkerThread
  fun syncRecipientInfoWithSystemContacts(context: Context) {
    DirectoryHelper.syncRecipientInfoWithSystemContacts(context)
  }

  @JvmStatic
  fun buildContactLinkConfiguration(context: Context, account: Account): ContactLinkConfiguration {
    return ContactLinkConfiguration(
      account = account,
      appName = context.getString(R.string.app_name),
      messagePrompt = { e164 -> context.getString(R.string.ContactsDatabase_message_s, e164) },
      callPrompt = { e164 -> context.getString(R.string.ContactsDatabase_signal_call_s, e164) },
      e164Formatter = { number -> PhoneNumberFormatter.get(context).format(number) },
      messageMimetype = MESSAGE_MIMETYPE,
      callMimetype = CALL_MIMETYPE
    )
  }
}
