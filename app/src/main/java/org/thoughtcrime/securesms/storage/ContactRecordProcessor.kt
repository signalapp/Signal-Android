package org.thoughtcrime.securesms.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.isEmpty
import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.core.util.nullIfEmpty
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob.Companion.enqueue
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient.Companion.trustedPush
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncModels.localToRemoteRecord
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.signalAci
import org.whispersystems.signalservice.api.storage.signalPni
import org.whispersystems.signalservice.api.storage.toSignalContactRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState
import java.io.IOException
import java.util.Optional
import java.util.regex.Pattern

/**
 * Record processor for [SignalContactRecord].
 * Handles merging and updating our local store when processing remote contact storage records.
 */
class ContactRecordProcessor(
  private val selfAci: ACI?,
  private val selfPni: PNI?,
  private val selfE164: String?,
  private val recipientTable: RecipientTable
) : DefaultStorageRecordProcessor<SignalContactRecord>() {

  companion object {
    private val TAG = Log.tag(ContactRecordProcessor::class.java)

    private val E164_PATTERN: Pattern = Pattern.compile("^\\+[1-9]\\d{0,18}$")

    private fun isValidE164(value: String): Boolean {
      return E164_PATTERN.matcher(value).matches()
    }
  }

  constructor() : this(
    selfAci = SignalStore.account.aci,
    selfPni = SignalStore.account.pni,
    selfE164 = SignalStore.account.e164,
    recipientTable = SignalDatabase.recipients
  )

  /**
   * For contact records specifically, we have some extra work that needs to be done before we process all of the records.
   *
   * We have to find all unregistered ACI-only records and split them into two separate contact rows locally, if necessary.
   * The reasons are nuanced, but the TL;DR is that we want to split unregistered users into separate rows so that a user
   * could re-register and get a different ACI.
   */
  @Throws(IOException::class)
  override fun process(remoteRecords: Collection<SignalContactRecord>, keyGenerator: StorageKeyGenerator) {
    val unregisteredAciOnly: MutableList<SignalContactRecord> = ArrayList()

    for (remoteRecord in remoteRecords) {
      if (isInvalid(remoteRecord)) {
        continue
      }

      if (remoteRecord.proto.unregisteredAtTimestamp > 0 && remoteRecord.proto.signalAci != null && remoteRecord.proto.signalPni == null && remoteRecord.proto.e164.isBlank()) {
        unregisteredAciOnly.add(remoteRecord)
      }
    }

    if (unregisteredAciOnly.size > 0) {
      for (aciOnly in unregisteredAciOnly) {
        SignalDatabase.recipients.splitForStorageSyncIfNecessary(aciOnly.proto.signalAci!!)
      }
    }

    super.process(remoteRecords, keyGenerator)
  }

  /**
   * Error cases:
   * - You can't have a contact record without an ACI or PNI.
   * - You can't have a contact record for yourself. That should be an account record.
   *
   * Note: This method could be written more succinctly, but the logs are useful :)
   */
  override fun isInvalid(remote: SignalContactRecord): Boolean {
    val hasAci = remote.proto.signalAci?.isValid == true
    val hasPni = remote.proto.signalPni?.isValid == true

    if (!hasAci && !hasPni) {
      Log.w(TAG, "Found a ContactRecord with neither an ACI nor a PNI -- marking as invalid.")
      return true
    } else if (selfAci != null && selfAci == remote.proto.signalAci ||
      (selfPni != null && selfPni == remote.proto.signalPni) ||
      (selfE164 != null && remote.proto.e164.isNotBlank() && remote.proto.e164 == selfE164)
    ) {
      Log.w(TAG, "Found a ContactRecord for ourselves -- marking as invalid.")
      return true
    } else if (remote.proto.e164.isNotBlank() && !isValidE164(remote.proto.e164)) {
      Log.w(TAG, "Found a record with an invalid E164. Marking as invalid.")
      return true
    } else {
      return false
    }
  }

  override fun getMatching(remote: SignalContactRecord, keyGenerator: StorageKeyGenerator): Optional<SignalContactRecord> {
    var found: Optional<RecipientId> = remote.proto.signalAci?.let { recipientTable.getByAci(it) } ?: Optional.empty()

    if (found.isEmpty && remote.proto.e164.isNotBlank()) {
      found = recipientTable.getByE164(remote.proto.e164)
    }

    if (found.isEmpty && remote.proto.signalPni != null) {
      found = recipientTable.getByPni(remote.proto.signalPni!!)
    }

    return found
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord ->
        if (settings.storageId != null) {
          return@map localToRemoteRecord(settings)
        } else {
          Log.w(TAG, "Newly discovering a registered user via storage service. Saving a storageId for them.")
          recipientTable.updateStorageId(settings.id, keyGenerator.generate())

          val updatedSettings = recipientTable.getRecordForSync(settings.id)!!
          return@map localToRemoteRecord(updatedSettings)
        }
      }
      .map { record -> SignalContactRecord(record.id, record.proto.contact!!) }
  }

  override fun merge(remote: SignalContactRecord, local: SignalContactRecord, keyGenerator: StorageKeyGenerator): SignalContactRecord {
    val mergedProfileGivenName: String
    val mergedProfileFamilyName: String

    val localAci = local.proto.signalAci
    val localPni = local.proto.signalPni

    val remoteAci = remote.proto.signalAci
    val remotePni = remote.proto.signalPni

    if (remote.proto.givenName.isNotBlank() || remote.proto.familyName.isNotBlank()) {
      mergedProfileGivenName = remote.proto.givenName
      mergedProfileFamilyName = remote.proto.familyName
    } else {
      mergedProfileGivenName = local.proto.givenName
      mergedProfileFamilyName = local.proto.familyName
    }

    val mergedIdentityState: IdentityState
    val mergedIdentityKey: ByteArray?

    if ((remote.proto.identityState != local.proto.identityState && remote.proto.identityKey.isNotEmpty()) ||
      (remote.proto.identityKey.isNotEmpty() && local.proto.identityKey.isEmpty()) ||
      (remote.proto.identityKey.isNotEmpty() && local.proto.unregisteredAtTimestamp > 0)
    ) {
      mergedIdentityState = remote.proto.identityState
      mergedIdentityKey = remote.proto.identityKey.takeIf { it.isNotEmpty() }?.toByteArray()
    } else {
      mergedIdentityState = local.proto.identityState
      mergedIdentityKey = local.proto.identityKey.takeIf { it.isNotEmpty() }?.toByteArray()
    }

    if (localAci != null && mergedIdentityKey != null && remote.proto.identityKey.isNotEmpty() && !mergedIdentityKey.contentEquals(remote.proto.identityKey.toByteArray())) {
      Log.w(TAG, "The local and remote identity keys do not match for " + localAci + ". Enqueueing a profile fetch.")
      enqueue(trustedPush(localAci, localPni, local.proto.e164).id)
    }

    val mergedPni: PNI?
    val mergedE164: String?

    val e164sMatchButPnisDont = local.proto.e164.isNotBlank() &&
      local.proto.e164 == remote.proto.e164 &&
      localPni != null &&
      remotePni != null &&
      localPni != remotePni

    val pnisMatchButE164sDont = localPni != null &&
      localPni == remotePni &&
      local.proto.e164.isNotBlank() &&
      remote.proto.e164.isNotBlank() &&
      local.proto.e164 != remote.proto.e164

    if (e164sMatchButPnisDont) {
      Log.w(TAG, "Matching E164s, but the PNIs differ! Trusting our local pair.")
      // TODO [pnp] Schedule CDS fetch?
      mergedPni = localPni
      mergedE164 = local.proto.e164
    } else if (pnisMatchButE164sDont) {
      Log.w(TAG, "Matching PNIs, but the E164s differ! Trusting our local pair.")
      // TODO [pnp] Schedule CDS fetch?
      mergedPni = localPni
      mergedE164 = local.proto.e164
    } else {
      mergedPni = remotePni ?: localPni
      mergedE164 = remote.proto.e164.nullIfBlank() ?: local.proto.e164.nullIfBlank()
    }

    val merged = SignalContactRecord.newBuilder(remote.serializedUnknowns).apply {
      e164 = mergedE164 ?: ""
      aci = local.proto.aci.nullIfBlank() ?: remote.proto.aci
      pni = mergedPni?.toStringWithoutPrefix() ?: ""
      givenName = mergedProfileGivenName
      familyName = mergedProfileFamilyName
      profileKey = remote.proto.profileKey.nullIfEmpty() ?: local.proto.profileKey
      username = remote.proto.username.nullIfBlank() ?: local.proto.username
      identityState = mergedIdentityState
      identityKey = mergedIdentityKey?.toByteString() ?: ByteString.EMPTY
      blocked = remote.proto.blocked
      whitelisted = remote.proto.whitelisted
      archived = remote.proto.archived
      markedUnread = remote.proto.markedUnread
      mutedUntilTimestamp = remote.proto.mutedUntilTimestamp
      hideStory = remote.proto.hideStory
      unregisteredAtTimestamp = remote.proto.unregisteredAtTimestamp
      hidden = remote.proto.hidden
      systemGivenName = if (SignalStore.account.isPrimaryDevice) local.proto.systemGivenName else remote.proto.systemGivenName
      systemFamilyName = if (SignalStore.account.isPrimaryDevice) local.proto.systemFamilyName else remote.proto.systemFamilyName
      systemNickname = remote.proto.systemNickname
      nickname = remote.proto.nickname
      pniSignatureVerified = remote.proto.pniSignatureVerified || local.proto.pniSignatureVerified
      note = remote.proto.note.nullIfBlank() ?: ""
      avatarColor = local.proto.avatarColor
    }.build().toSignalContactRecord(StorageId.forContact(keyGenerator.generate()))

    val matchesRemote = doParamsMatch(remote, merged)
    val matchesLocal = doParamsMatch(local, merged)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      merged
    }
  }

  override fun insertLocal(record: SignalContactRecord) {
    recipientTable.applyStorageSyncContactInsert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalContactRecord>) {
    recipientTable.applyStorageSyncContactUpdate(update)
  }

  override fun compare(lhs: SignalContactRecord, rhs: SignalContactRecord): Int {
    return if (
      (lhs.proto.signalAci != null && lhs.proto.aci == rhs.proto.aci) ||
      (lhs.proto.e164.isNotBlank() && lhs.proto.e164 == rhs.proto.e164) ||
      (lhs.proto.signalPni != null && lhs.proto.pni == rhs.proto.pni)
    ) {
      0
    } else {
      1
    }
  }
}
