package org.thoughtcrime.securesms.jobs

import android.Manifest
import android.accounts.Account
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import org.signal.contacts.ContactLinkConfiguration
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import java.lang.Exception

/**
 * This job makes sure all of the contact "links" are up-to-date. The links are the actions you see when you look at a Signal user in your system contacts
 * that let you send a message or start a call.
 */
class SyncSystemContactLinksJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  constructor() : this(
    Parameters.Builder()
      .setQueue("SyncSystemContactLinksJob")
      .setMaxAttempts(1)
      .setMaxInstancesForQueue(2)
      .build()
  )

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey() = KEY
  override fun onFailure() = Unit
  override fun onShouldRetry(e: Exception) = false

  override fun onRun() {
    if (!Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      Log.w(TAG, "No contact permissions. Skipping.")
      return
    }

    val stopwatch = Stopwatch("contact-links")

    val registeredE164s: Set<String> = SignalDatabase.recipients.getRegisteredE164s()

    if (registeredE164s.isEmpty()) {
      Log.w(TAG, "No registeredE164s. Skipping.")
      return
    }

    stopwatch.split("fetch-e164s")

    val account = SystemContactsRepository.getOrCreateSystemAccount(context, BuildConfig.APPLICATION_ID, context.getString(R.string.app_name))
    if (account == null) {
      Log.w(TAG, "Failed to create an account!")
      return
    }

    try {
      SystemContactsRepository.removeDeletedRawContactsForAccount(context, account)
      stopwatch.split("delete-stragglers")

      SystemContactsRepository.addMessageAndCallLinksToContacts(
        context = context,
        config = buildContactLinkConfiguration(context, account),
        targetE164s = registeredE164s,
        removeIfMissing = true
      )
      stopwatch.split("add-links")

      stopwatch.stop(TAG)
    } catch (e: RemoteException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    } catch (e: OperationApplicationException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    }
  }

  private fun buildContactLinkConfiguration(context: Context, account: Account): ContactLinkConfiguration {
    return ContactLinkConfiguration(
      account = account,
      appName = context.getString(R.string.app_name),
      messagePrompt = { e164 -> context.getString(R.string.ContactsDatabase_message_s, e164) },
      callPrompt = { e164 -> context.getString(R.string.ContactsDatabase_signal_call_s, e164) },
      e164Formatter = { number -> PhoneNumberFormatter.get(context).format(number) },
      messageMimetype = MESSAGE_MIMETYPE,
      callMimetype = CALL_MIMETYPE,
      syncTag = CONTACT_TAG
    )
  }

  class Factory : Job.Factory<SyncSystemContactLinksJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?) = SyncSystemContactLinksJob(parameters)
  }

  companion object {
    private val TAG = Log.tag(SyncSystemContactLinksJob::class.java)

    const val KEY = "SyncSystemContactLinksJob"

    private const val MESSAGE_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
    private const val CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
    private const val CONTACT_TAG = "__TS"
  }
}
