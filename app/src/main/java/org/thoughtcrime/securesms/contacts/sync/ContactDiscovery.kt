package org.thoughtcrime.securesms.contacts.sync

import android.Manifest
import android.accounts.Account
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.WorkerThread
import org.signal.contacts.ContactLinkConfiguration
import org.signal.contacts.SystemContactsRepository.addMessageAndCallLinksToContacts
import org.signal.contacts.SystemContactsRepository.getAllSystemContacts
import org.signal.contacts.SystemContactsRepository.getOrCreateSystemAccount
import org.signal.contacts.SystemContactsRepository.removeDeletedRawContactsForAccount
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.sms.IncomingJoinedMessage
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Stopwatch
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.util.Calendar

/**
 * Methods for discovering which users are registered and marking them as such in the database.
 */
object ContactDiscovery {

  private val TAG = Log.tag(ContactDiscovery::class.java)

  private const val MESSAGE_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
  private const val CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
  private const val CONTACT_TAG = "__TS"

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refreshAll(context: Context, notifyOfNewUsers: Boolean) {
    if (TextUtils.isEmpty(SignalStore.account().e164)) {
      Log.w(TAG, "Have not yet set our own local number. Skipping.")
      return
    }

    if (!hasContactsPermissions(context)) {
      Log.w(TAG, "No contact permissions. Skipping.")
      return
    }

    if (!SignalStore.registrationValues().isRegistrationComplete) {
      Log.w(TAG, "Registration is not yet complete. Skipping, but running a routine to possibly mark it complete.")
      RegistrationUtil.maybeMarkRegistrationComplete(context)
      return
    }

    refreshRecipients(
      context = context,
      descriptor = "refresh-all",
      refresh = {
        ContactDiscoveryRefreshV1.refreshAll(context)
      },
      removeSystemContactLinksIfMissing = true,
      notifyOfNewUsers = notifyOfNewUsers
    )

    StorageSyncHelper.scheduleSyncForDataChange()
  }

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipients: List<Recipient>, notifyOfNewUsers: Boolean) {
    refreshRecipients(
      context = context,
      descriptor = "refresh-multiple",
      refresh = {
        ContactDiscoveryRefreshV1.refresh(context, recipients)
      },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )
  }

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipient: Recipient, notifyOfNewUsers: Boolean): RecipientDatabase.RegisteredState {
    val result: RefreshResult = refreshRecipients(
      context = context,
      descriptor = "refresh-single",
      refresh = {
        ContactDiscoveryRefreshV1.refresh(context, listOf(recipient))
      },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )

    return if (result.registeredIds.contains(recipient.id)) {
      RecipientDatabase.RegisteredState.REGISTERED
    } else {
      RecipientDatabase.RegisteredState.NOT_REGISTERED
    }
  }

  @JvmStatic
  @WorkerThread
  fun syncRecipientInfoWithSystemContacts(context: Context) {
    syncRecipientsWithSystemContacts(context, emptyMap())
  }

  private fun refreshRecipients(
    context: Context,
    descriptor: String,
    refresh: () -> RefreshResult,
    removeSystemContactLinksIfMissing: Boolean,
    notifyOfNewUsers: Boolean
  ): RefreshResult {
    val stopwatch = Stopwatch(descriptor)

    val preExistingRegisteredIds: Set<RecipientId> = SignalDatabase.recipients.getRegistered().toSet()
    stopwatch.split("pre-existing")

    val result: RefreshResult = refresh()
    stopwatch.split("cds")

    if (hasContactsPermissions(context)) {
      addSystemContactLinks(context, result.registeredIds, removeSystemContactLinksIfMissing)
      stopwatch.split("contact-links")

      syncRecipientsWithSystemContacts(context, result.rewrites)
      stopwatch.split("contact-sync")

      if (TextSecurePreferences.hasSuccessfullyRetrievedDirectory(context) && notifyOfNewUsers) {
        val systemContacts: Set<RecipientId> = SignalDatabase.recipients.getSystemContacts().toSet()
        val newlyRegisteredSystemContacts: Set<RecipientId> = (result.registeredIds - preExistingRegisteredIds).intersect(systemContacts)

        notifyNewUsers(context, newlyRegisteredSystemContacts)
      } else {
        TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true)
      }
      stopwatch.split("notify")
    } else {
      Log.w(TAG, "No contacts permission, can't sync with system contacts.")
    }

    stopwatch.stop(TAG)

    return result
  }

  private fun notifyNewUsers(context: Context, newUserIds: Collection<RecipientId>) {
    if (!SignalStore.settings().isNotifyWhenContactJoinsSignal) return

    Recipient.resolvedList(newUserIds)
      .filter { !it.isSelf && it.hasAUserSetDisplayName(context) && !hasSession(it.id) }
      .map { IncomingJoinedMessage(it.id) }
      .map { SignalDatabase.sms.insertMessageInbox(it) }
      .filter { it.isPresent }
      .map { it.get() }
      .forEach { result ->
        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        if (hour in 9..22) {
          ApplicationDependencies.getMessageNotifier().updateNotification(context, result.threadId, true)
        } else {
          Log.i(TAG, "Not notifying of a new user due to the time of day. (Hour: $hour)")
        }
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

  private fun hasContactsPermissions(context: Context): Boolean {
    return Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
  }

  /**
   * Adds the "Message/Call $number with Signal" link to registered users in the system contacts.
   * @param registeredIds A list of registered [RecipientId]s
   * @param removeIfMissing If true, this will remove links from every currently-linked system contact that is *not* in the [registeredIds] list.
   */
  private fun addSystemContactLinks(context: Context, registeredIds: Collection<RecipientId>, removeIfMissing: Boolean) {
    if (!Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      Log.w(TAG, "[addSystemContactLinks] No contact permissions. Skipping.")
      return
    }

    if (registeredIds.isEmpty()) {
      Log.w(TAG, "[addSystemContactLinks] No registeredIds. Skipping.")
      return
    }

    val stopwatch = Stopwatch("contact-links")

    val account = getOrCreateSystemAccount(context, BuildConfig.APPLICATION_ID, context.getString(R.string.app_name))
    if (account == null) {
      Log.w(TAG, "[addSystemContactLinks] Failed to create an account!")
      return
    }

    try {
      val registeredE164s: Set<String> = SignalDatabase.recipients.getE164sForIds(registeredIds)
      stopwatch.split("fetch-e164s")

      removeDeletedRawContactsForAccount(context, account)
      stopwatch.split("delete-stragglers")

      addMessageAndCallLinksToContacts(
        context = context,
        config = buildContactLinkConfiguration(context, account),
        targetE164s = registeredE164s,
        removeIfMissing = removeIfMissing
      )
      stopwatch.split("add-links")

      stopwatch.stop(TAG)
    } catch (e: RemoteException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    } catch (e: OperationApplicationException) {
      Log.w(TAG, "[addSystemContactLinks] Failed to add links to contacts.", e)
    }
  }

  /**
   * Synchronizes info from the system contacts (name, avatar, etc)
   */
  private fun syncRecipientsWithSystemContacts(context: Context, rewrites: Map<String, String>) {
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate()
    try {
      getAllSystemContacts(context) { PhoneNumberFormatter.get(context).format(it) }.use { iterator ->
        while (iterator.hasNext()) {
          val details = iterator.next()
          val name = StructuredNameRecord(details.givenName, details.familyName)
          val phones = details.numbers
            .map { phoneDetails ->
              val realNumber = Util.getFirstNonEmpty(rewrites[phoneDetails.number], phoneDetails.number)
              PhoneNumberRecord.Builder()
                .withRecipientId(Recipient.externalContact(context, realNumber).id)
                .withContactUri(phoneDetails.contactUri)
                .withDisplayName(phoneDetails.displayName)
                .withContactPhotoUri(phoneDetails.photoUri)
                .withContactLabel(phoneDetails.label)
                .build()
            }
            .toList()

          ContactHolder().apply {
            setStructuredNameRecord(name)
            addPhoneNumberRecords(phones)
          }.commit(handle)
        }
      }
    } catch (e: IllegalStateException) {
      Log.w(TAG, "Hit an issue with the cursor while reading!", e)
    } finally {
      handle.finish()
    }

    if (NotificationChannels.supported()) {
      SignalDatabase.recipients.getRecipientsWithNotificationChannels().use { reader ->
        var recipient: Recipient? = reader.getNext()

        while (recipient != null) {
          NotificationChannels.updateContactChannelName(context, recipient)
          recipient = reader.getNext()
        }
      }
    }
  }

  /**
   * Whether or not a session exists with the provided recipient.
   */
  fun hasSession(id: RecipientId): Boolean {
    val recipient = Recipient.resolved(id)

    if (!recipient.hasServiceId()) {
      return false
    }

    val protocolAddress = Recipient.resolved(id).requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID)

    return ApplicationDependencies.getProtocolStore().aci().containsSession(protocolAddress) ||
      ApplicationDependencies.getProtocolStore().pni().containsSession(protocolAddress)
  }

  class RefreshResult(
    val registeredIds: Set<RecipientId>,
    val rewrites: Map<String, String>
  )
}
