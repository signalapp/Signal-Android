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
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.SignalE164Util
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

    val e164sForLinking: Set<String> = SignalDatabase.recipients.getE164sForSystemContactLinks()

    if (e164sForLinking.isEmpty()) {
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
        targetE164s = e164sForLinking,
        removeIfMissing = true
      )
      stopwatch.split("add-links")

      // Adding links changes how certain structured name records are stored, so we need to re-sync to make sure we get the latest structured name
      ContactDiscovery.syncRecipientInfoWithSystemContacts(context)
      StorageSyncHelper.scheduleSyncForDataChange()
      stopwatch.split("sync-contact-info")

      stopwatch.stop(TAG)
    } catch (e: RemoteException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    } catch (e: OperationApplicationException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    }
  }

  class Factory : Job.Factory<SyncSystemContactLinksJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?) = SyncSystemContactLinksJob(parameters)
  }

  companion object {
    private val TAG = Log.tag(SyncSystemContactLinksJob::class.java)

    const val KEY = "SyncSystemContactLinksJob"

    private const val MESSAGE_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
    private const val CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
    private const val VIDEO_CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.videocall"
    private const val CONTACT_TAG = "__TS"

    fun buildContactLinkConfiguration(context: Context, account: Account): ContactLinkConfiguration {
      return ContactLinkConfiguration(
        account = account,
        appName = context.getString(R.string.app_name),
        messagePrompt = { e164 -> context.getString(R.string.ContactsDatabase_message_s, e164) },
        callPrompt = { e164 -> context.getString(R.string.ContactsDatabase_signal_call_s, e164) },
        videoCallPrompt = { e164 -> context.getString(R.string.ContactsDatabase_signal_video_call_s, e164) },
        e164Formatter = { number -> SignalE164Util.formatAsE164(number) },
        messageMimetype = MESSAGE_MIMETYPE,
        callMimetype = CALL_MIMETYPE,
        videoCallMimetype = VIDEO_CALL_MIMETYPE,
        syncTag = CONTACT_TAG
      )
    }
  }
}
