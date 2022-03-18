package org.thoughtcrime.securesms.contacts.sync

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException

/**
 * Methods for discovering which users are registered and marking them as such in the database.
 */
object ContactDiscovery {

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
}
