package org.thoughtcrime.securesms.contacts.sync

import android.Manifest
import android.content.Context
import android.text.TextUtils
import androidx.annotation.WorkerThread
import org.signal.contacts.SystemContactsRepository
import org.signal.contacts.SystemContactsRepository.ContactIterator
import org.signal.contacts.SystemContactsRepository.ContactPhoneDetails
import org.signal.core.util.Stopwatch
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.SyncSystemContactLinksJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.sms.IncomingJoinedMessage
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
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
        ContactDiscoveryRefreshV2.refreshAll(context, useCompat = !FeatureFlags.phoneNumberPrivacy(), ignoreResults = false)
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
        ContactDiscoveryRefreshV2.refresh(context, recipients, useCompat = !FeatureFlags.phoneNumberPrivacy(), ignoreResults = false)
      },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )
  }

  @JvmStatic
  @Throws(IOException::class)
  @WorkerThread
  fun refresh(context: Context, recipient: Recipient, notifyOfNewUsers: Boolean): RecipientTable.RegisteredState {
    val result: RefreshResult = refreshRecipients(
      context = context,
      descriptor = "refresh-single",
      refresh = {
        ContactDiscoveryRefreshV2.refresh(context, listOf(recipient), useCompat = !FeatureFlags.phoneNumberPrivacy(), ignoreResults = false)
      },
      removeSystemContactLinksIfMissing = false,
      notifyOfNewUsers = notifyOfNewUsers
    )

    return if (result.registeredIds.contains(recipient.id)) {
      RecipientTable.RegisteredState.REGISTERED
    } else {
      RecipientTable.RegisteredState.NOT_REGISTERED
    }
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

  private fun phoneNumberFormatter(context: Context): (String) -> String {
    return { PhoneNumberFormatter.get(context).format(it) }
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
      ApplicationDependencies.getJobManager().add(SyncSystemContactLinksJob())

      val useFullSync = removeSystemContactLinksIfMissing && result.registeredIds.size > FULL_SYSTEM_CONTACT_SYNC_THRESHOLD
      syncRecipientsWithSystemContacts(
        context = context,
        rewrites = result.rewrites,
        contactsProvider = {
          if (useFullSync) {
            Log.d(TAG, "Doing a full system contact sync. There are ${result.registeredIds.size} contacts to get info for.")
            SystemContactsRepository.getAllSystemContacts(context, phoneNumberFormatter(context))
          } else {
            Log.d(TAG, "Doing a partial system contact sync. There are ${result.registeredIds.size} contacts to get info for.")
            SystemContactsRepository.getContactDetailsByQueries(
              context = context,
              queries = Recipient.resolvedList(result.registeredIds).mapNotNull { it.e164.orElse(null) },
              e164Formatter = phoneNumberFormatter(context)
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
    if (!SignalStore.settings().isNotifyWhenContactJoinsSignal) return

    Recipient.resolvedList(newUserIds)
      .filter { !it.isSelf && it.hasAUserSetDisplayName(context) && !hasSession(it.id) }
      .map { IncomingJoinedMessage(it.id) }
      .map { SignalDatabase.messages.insertMessageInbox(it) }
      .filter { it.isPresent }
      .map { it.get() }
      .forEach { result ->
        val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        if (hour in 9..22) {
          ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(result.threadId), true)
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
    contactsProvider: () -> ContactIterator = { SystemContactsRepository.getAllSystemContacts(context, phoneNumberFormatter(context)) },
    clearInfoForMissingContacts: Boolean
  ) {
    val localNumber: String = SignalStore.account().e164 ?: ""
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

            handle.setSystemContactInfo(
              Recipient.externalContact(realNumber).id,
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
