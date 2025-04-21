package org.thoughtcrime.securesms.contacts.sync

import android.Manifest
import android.content.Context
import android.text.TextUtils
import androidx.annotation.WorkerThread
import org.signal.contacts.SystemContactsRepository
import org.signal.contacts.SystemContactsRepository.ContactIterator
import org.signal.contacts.SystemContactsRepository.ContactPhoneDetails
import org.signal.core.util.E164Util
import org.signal.core.util.Stopwatch
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.SyncSystemContactLinksJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.util.UuidUtil
import java.io.IOException
import java.util.Calendar

/**
 * Methods for discovering which users are registered and marking them as such in the database.
 */
object ContactDiscovery {

  private val TAG = Log.tag(ContactDiscovery::class.java)

  private const val FULL_SYSTEM_CONTACT_SYNC_THRESHOLD = 3

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refreshAll(context: Context, notifyOfNewUsers: Boolean) {
    if (TextUtils.isEmpty(SignalStore.account.e164)) {
      Log.w(TAG, "Have not yet set our own local number. Skipping.")
      return
    }

    if (!hasContactsPermissions(context)) {
      Log.w(TAG, "No contact permissions. Skipping.")
      return
    }

    if (!SignalStore.registration.isRegistrationComplete) {
      if (SignalStore.account.isRegistered && SignalStore.svr.lastPinCreateFailed()) {
        Log.w(TAG, "Registration isn't complete, but only because PIN creation failed. Allowing CDS to continue.")
      } else {
        Log.w(TAG, "Registration is not yet complete. Skipping, but running a routine to possibly mark it complete.")
        RegistrationUtil.maybeMarkRegistrationComplete()
        return
      }
    }

    refreshRecipients(
      context = context,
      descriptor = "refresh-all",
      refresh = {
        ContactDiscoveryRefreshV2.refreshAll(context)
      },
      removeSystemContactLinksIfMissing = true,
      notifyOfNewUsers = notifyOfNewUsers,
      forceFullSystemContactSync = true
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
      refresh = { ContactDiscoveryRefreshV2.refresh(context, recipients) },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )
  }

  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipient: Recipient, notifyOfNewUsers: Boolean, timeoutMs: Long? = null): RecipientTable.RegisteredState {
    val result: RefreshResult = refreshRecipients(
      context = context,
      descriptor = "refresh-single",
      refresh = { ContactDiscoveryRefreshV2.refresh(context, listOf(recipient), timeoutMs = timeoutMs) },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )

    return if (result.registeredIds.contains(recipient.id)) {
      RecipientTable.RegisteredState.REGISTERED
    } else {
      RecipientTable.RegisteredState.NOT_REGISTERED
    }
  }

  /**
   * Looks up the PNI/ACI for an E164. Only creates a recipient if the number is in the CDS directory.
   * Use sparingly! This will always use up the user's CDS quota. Always prefer other syncing methods for bulk lookups.
   *
   * Returns a [LookupResult] if the E164 is in the CDS directory, or null if it is not.
   * Important: Just because a user is not in the directory does not mean they are not registered. They could have discoverability off.
   */
  @Throws(IOException::class)
  @WorkerThread
  fun lookupE164(e164: String): LookupResult? {
    return ContactDiscoveryRefreshV2.lookupE164(e164)
  }

  @JvmStatic
  @WorkerThread
  fun syncRecipientInfoWithSystemContacts(context: Context) {
    if (!hasContactsPermissions(context)) {
      Log.w(TAG, "[syncRecipientInfoWithSystemContacts] No contacts permission, skipping.")
      return
    }

    syncRecipientsWithSystemContacts(
      context = context,
      rewrites = emptyMap(),
      clearInfoForMissingContacts = true
    )
  }

  private fun phoneNumberFormatter(): (String) -> String? {
    val formatter = E164Util.createFormatterForE164(SignalStore.account.e164!!)
    return { formatter.formatAsE164(it) }
  }

  private fun refreshRecipients(
    context: Context,
    descriptor: String,
    refresh: () -> RefreshResult,
    removeSystemContactLinksIfMissing: Boolean,
    notifyOfNewUsers: Boolean,
    forceFullSystemContactSync: Boolean = false
  ): RefreshResult {
    val stopwatch = Stopwatch(descriptor)

    val preExistingRegisteredIds: Set<RecipientId> = SignalDatabase.recipients.getRegistered()
    stopwatch.split("pre-existing")

    val result: RefreshResult = refresh()
    stopwatch.split("cds")

    if (hasContactsPermissions(context)) {
      AppDependencies.jobManager.add(SyncSystemContactLinksJob())

      val useFullSync = forceFullSystemContactSync || (removeSystemContactLinksIfMissing && result.registeredIds.size > FULL_SYSTEM_CONTACT_SYNC_THRESHOLD)
      syncRecipientsWithSystemContacts(
        context = context,
        rewrites = result.rewrites,
        contactsProvider = {
          if (useFullSync) {
            Log.d(TAG, "Doing a full system contact sync. There are ${result.registeredIds.size} contacts to get info for.")
            SystemContactsRepository.getAllSystemContacts(context, phoneNumberFormatter())
          } else {
            Log.d(TAG, "Doing a partial system contact sync. There are ${result.registeredIds.size} contacts to get info for.")
            SystemContactsRepository.getContactDetailsByQueries(
              context = context,
              queries = Recipient.resolvedList(result.registeredIds).mapNotNull { it.e164.orElse(null) },
              e164Formatter = phoneNumberFormatter()
            )
          }
        },
        clearInfoForMissingContacts = useFullSync
      )
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
    if (!SignalStore.settings.isNotifyWhenContactJoinsSignal) return

    Recipient.resolvedList(newUserIds)
      .filter { !it.isSelf && it.hasAUserSetDisplayName(context) && !hasSession(it.id) && it.hasE164 && !it.isBlocked }
      .map {
        Log.i(TAG, "Inserting 'contact joined' message for ${it.id}. E164: ${it.e164}")
        val message = IncomingMessage.contactJoined(it.id, System.currentTimeMillis())
        SignalDatabase.messages.insertMessageInbox(message)
      }
      .filter { it.isPresent }
      .map { it.get() }
      .forEach { result ->
        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        if (hour in 9..22) {
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(result.threadId))
        } else {
          Log.i(TAG, "Not notifying of a new user due to the time of day. (Hour: $hour)")
        }
      }
  }

  private fun hasContactsPermissions(context: Context): Boolean {
    return Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
  }

  /**
   * Synchronizes info from the system contacts (name, avatar, etc)
   */
  private fun syncRecipientsWithSystemContacts(
    context: Context,
    rewrites: Map<String, String>,
    contactsProvider: () -> ContactIterator = { SystemContactsRepository.getAllSystemContacts(context, phoneNumberFormatter()) },
    clearInfoForMissingContacts: Boolean
  ) {
    val localNumber: String = SignalStore.account.e164 ?: ""
    val handle = SignalDatabase.recipients.beginBulkSystemContactUpdate(clearInfoForMissingContacts)
    try {
      contactsProvider().use { iterator ->
        while (iterator.hasNext()) {
          val details = iterator.next()
          val phoneDetailsWithoutSelf: List<ContactPhoneDetails> = details.numbers
            .filter { it.number != localNumber }
            .filterNot { UuidUtil.isUuid(it.number) }

          for (phoneDetails in phoneDetailsWithoutSelf) {
            val realNumber: String = Util.getFirstNonEmpty(rewrites[phoneDetails.number], phoneDetails.number)

            val profileName: ProfileName = if (!StringUtil.isEmpty(details.givenName)) {
              ProfileName.fromParts(details.givenName, details.familyName)
            } else if (!StringUtil.isEmpty(phoneDetails.displayName)) {
              ProfileName.asGiven(phoneDetails.displayName)
            } else {
              ProfileName.EMPTY
            }

            val recipient: Recipient = Recipient.externalContact(realNumber) ?: continue

            handle.setSystemContactInfo(
              recipient.id,
              profileName,
              phoneDetails.displayName,
              phoneDetails.photoUri,
              phoneDetails.label,
              phoneDetails.type,
              phoneDetails.contactUri.toString()
            )
          }
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
          NotificationChannels.getInstance().updateContactChannelName(recipient)
          recipient = reader.getNext()
        }
      }
    }
  }

  /**
   * Whether or not a session exists with the provided recipient.
   */
  private fun hasSession(id: RecipientId): Boolean {
    val recipient = Recipient.resolved(id)

    if (!recipient.hasServiceId) {
      return false
    }

    val protocolAddress = Recipient.resolved(id).requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID)

    return AppDependencies.protocolStore.aci().containsSession(protocolAddress) ||
      AppDependencies.protocolStore.pni().containsSession(protocolAddress)
  }

  class RefreshResult(
    val registeredIds: Set<RecipientId>,
    val rewrites: Map<String, String>
  )

  data class LookupResult(
    val recipientId: RecipientId,
    val pni: ServiceId.PNI,
    val aci: ServiceId.ACI?
  )
}
