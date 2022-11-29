package org.thoughtcrime.securesms.components.settings.app.changenumber

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.protocol.util.Medium
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingChangeNumberMetadata
import org.thoughtcrime.securesms.database.model.toProtoByteString
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.CertificateType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.KbsRepository
import org.thoughtcrime.securesms.pin.KeyBackupSystemWrongPinException
import org.thoughtcrime.securesms.pin.TokenData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.VerifyAccountRepository
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.KbsPinData
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.SignalServiceMessageSender
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

private val TAG: String = Log.tag(ChangeNumberRepository::class.java)

/**
 * Provides various change number operations. All operations must run on [Schedulers.single] to support
 * the global "I am changing the number" lock exclusivity.
 */
class ChangeNumberRepository(
  private val accountManager: SignalServiceAccountManager = ApplicationDependencies.getSignalServiceAccountManager(),
  private val messageSender: SignalServiceMessageSender = ApplicationDependencies.getSignalServiceMessageSender()
) {

  companion object {
    /**
     * This lock should be held by anyone who is performing a change number operation, so that two different parties cannot change the user's number
     * at the same time.
     */
    val CHANGE_NUMBER_LOCK = ReentrantLock()

    /**
     * Adds Rx operators to chain to acquire and release the [CHANGE_NUMBER_LOCK] on subscribe and on finish.
     */
    fun <T : Any> acquireReleaseChangeNumberLock(upstream: Single<T>): Single<T> {
      return upstream.doOnSubscribe {
        CHANGE_NUMBER_LOCK.lock()
        SignalStore.misc().lockChangeNumber()
      }
        .subscribeOn(Schedulers.single())
        .observeOn(Schedulers.single())
        .doFinally {
          if (CHANGE_NUMBER_LOCK.isHeldByCurrentThread) {
            CHANGE_NUMBER_LOCK.unlock()
          }
        }
    }
  }

  fun ensureDecryptionsDrained(): Completable {
    return Completable.create { emitter ->
      ApplicationDependencies
        .getIncomingMessageObserver()
        .addDecryptionDrainedListener {
          emitter.onComplete()
        }
    }.subscribeOn(Schedulers.single())
      .timeout(15, TimeUnit.SECONDS)
  }

  fun changeNumber(code: String, newE164: String, pniUpdateMode: Boolean = false): Single<ServiceResponse<VerifyAccountResponse>> {
    return Single.fromCallable {
      var completed = false
      var attempts = 0
      lateinit var changeNumberResponse: ServiceResponse<VerifyAccountResponse>

      while (!completed && attempts < 5) {
        val (request: ChangePhoneNumberRequest, metadata: PendingChangeNumberMetadata) = createChangeNumberRequest(
          code = code,
          newE164 = newE164,
          registrationLock = null,
          pniUpdateMode = pniUpdateMode
        )

        SignalStore.misc().setPendingChangeNumberMetadata(metadata)

        changeNumberResponse = accountManager.changeNumber(request)

        val possibleError: Throwable? = changeNumberResponse.applicationError.orElse(null)
        if (possibleError is MismatchedDevicesException) {
          messageSender.handleChangeNumberMismatchDevices(possibleError.mismatchedDevices)
          attempts++
        } else {
          completed = true
        }
      }

      changeNumberResponse
    }.subscribeOn(Schedulers.single())
      .onErrorReturn { t -> ServiceResponse.forExecutionError(t) }
  }

  fun changeNumber(
    code: String,
    newE164: String,
    pin: String,
    tokenData: TokenData
  ): Single<ServiceResponse<VerifyAccountRepository.VerifyAccountWithRegistrationLockResponse>> {
    return Single.fromCallable {
      val kbsData: KbsPinData
      val registrationLock: String

      try {
        kbsData = KbsRepository.restoreMasterKey(pin, tokenData.enclave, tokenData.basicAuth, tokenData.tokenResponse)!!
        registrationLock = kbsData.masterKey.deriveRegistrationLock()
      } catch (e: KeyBackupSystemWrongPinException) {
        return@fromCallable ServiceResponse.forExecutionError(e)
      } catch (e: KeyBackupSystemNoDataException) {
        return@fromCallable ServiceResponse.forExecutionError(e)
      } catch (e: IOException) {
        return@fromCallable ServiceResponse.forExecutionError(e)
      }

      var completed = false
      var attempts = 0
      lateinit var changeNumberResponse: ServiceResponse<VerifyAccountResponse>

      while (!completed && attempts < 5) {
        val (request: ChangePhoneNumberRequest, metadata: PendingChangeNumberMetadata) = createChangeNumberRequest(
          code = code,
          newE164 = newE164,
          registrationLock = registrationLock,
          pniUpdateMode = false
        )

        SignalStore.misc().setPendingChangeNumberMetadata(metadata)

        changeNumberResponse = accountManager.changeNumber(request)

        val possibleError: Throwable? = changeNumberResponse.applicationError.orElse(null)
        if (possibleError is MismatchedDevicesException) {
          messageSender.handleChangeNumberMismatchDevices(possibleError.mismatchedDevices)
          attempts++
        } else {
          completed = true
        }
      }

      VerifyAccountRepository.VerifyAccountWithRegistrationLockResponse.from(changeNumberResponse, kbsData)
    }.subscribeOn(Schedulers.single())
      .onErrorReturn { t -> ServiceResponse.forExecutionError(t) }
  }

  @Suppress("UsePropertyAccessSyntax")
  fun whoAmI(): Single<WhoAmIResponse> {
    return Single.fromCallable { ApplicationDependencies.getSignalServiceAccountManager().getWhoAmI() }
      .subscribeOn(Schedulers.single())
  }

  @WorkerThread
  fun changeLocalNumber(e164: String, pni: PNI): Single<Unit> {
    val oldStorageId: ByteArray? = Recipient.self().storageServiceId
    SignalDatabase.recipients.updateSelfPhone(e164, pni)
    val newStorageId: ByteArray? = Recipient.self().storageServiceId

    if (e164 != SignalStore.account().requireE164() && MessageDigest.isEqual(oldStorageId, newStorageId)) {
      Log.w(TAG, "Self storage id was not rotated, attempting to rotate again")
      SignalDatabase.recipients.rotateStorageId(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      val secondAttemptStorageId: ByteArray? = Recipient.self().storageServiceId
      if (MessageDigest.isEqual(oldStorageId, secondAttemptStorageId)) {
        Log.w(TAG, "Second attempt also failed to rotate storage id")
      }
    }

    ApplicationDependencies.getRecipientCache().clear()

    SignalStore.account().setE164(e164)
    SignalStore.account().setPni(pni)

    ApplicationDependencies.getGroupsV2Authorization().clear()

    val metadata: PendingChangeNumberMetadata? = SignalStore.misc().pendingChangeNumberMetadata
    if (metadata == null) {
      Log.w(TAG, "No change number metadata, this shouldn't happen")
      throw AssertionError("No change number metadata")
    }

    val originalPni = ServiceId.fromByteString(metadata.previousPni)

    if (originalPni == pni) {
      Log.i(TAG, "No change has occurred, PNI is unchanged: $pni")
    } else {
      val pniIdentityKeyPair = IdentityKeyPair(metadata.pniIdentityKeyPair.toByteArray())
      val pniRegistrationId = metadata.pniRegistrationId
      val pniSignedPreyKeyId = metadata.pniSignedPreKeyId

      val pniProtocolStore = ApplicationDependencies.getProtocolStore().pni()
      val pniMetadataStore = SignalStore.account().pniPreKeys

      SignalStore.account().pniRegistrationId = pniRegistrationId
      SignalStore.account().setPniIdentityKeyAfterChangeNumber(pniIdentityKeyPair)

      val signedPreKey = pniProtocolStore.loadSignedPreKey(pniSignedPreyKeyId)
      val oneTimePreKeys = PreKeyUtil.generateAndStoreOneTimePreKeys(pniProtocolStore, pniMetadataStore)

      pniMetadataStore.activeSignedPreKeyId = signedPreKey.id
      accountManager.setPreKeys(ServiceIdType.PNI, pniProtocolStore.identityKeyPair.publicKey, signedPreKey, oneTimePreKeys)
      pniMetadataStore.isSignedPreKeyRegistered = true

      pniProtocolStore.identities().saveIdentityWithoutSideEffects(
        Recipient.self().id,
        pniProtocolStore.identityKeyPair.publicKey,
        IdentityTable.VerifiedStatus.VERIFIED,
        true,
        System.currentTimeMillis(),
        true
      )

      SignalStore.misc().setPniInitializedDevices(true)
      ApplicationDependencies.getGroupsV2Authorization().clear()
    }

    Recipient.self().live().refresh()
    StorageSyncHelper.scheduleSyncForDataChange()

    ApplicationDependencies.closeConnections()
    ApplicationDependencies.getIncomingMessageObserver()

    return rotateCertificates()
  }

  @Suppress("UsePropertyAccessSyntax")
  private fun rotateCertificates(): Single<Unit> {
    val certificateTypes = SignalStore.phoneNumberPrivacy().allCertificateTypes

    Log.i(TAG, "Rotating these certificates $certificateTypes")

    return Single.fromCallable {
      for (certificateType in certificateTypes) {
        val certificate: ByteArray? = when (certificateType) {
          CertificateType.UUID_AND_E164 -> accountManager.getSenderCertificate()
          CertificateType.UUID_ONLY -> accountManager.getSenderCertificateForPhoneNumberPrivacy()
          else -> throw AssertionError()
        }

        Log.i(TAG, "Successfully got $certificateType certificate")

        SignalStore.certificateValues().setUnidentifiedAccessCertificate(certificateType, certificate)
      }
    }.subscribeOn(Schedulers.single())
  }

  @Suppress("UsePropertyAccessSyntax")
  @WorkerThread
  private fun createChangeNumberRequest(
    code: String,
    newE164: String,
    registrationLock: String?,
    pniUpdateMode: Boolean
  ): ChangeNumberRequestData {
    val selfIdentifier: String = SignalStore.account().requireAci().toString()
    val aciProtocolStore: SignalProtocolStore = ApplicationDependencies.getProtocolStore().aci()

    val pniIdentity: IdentityKeyPair = if (pniUpdateMode) SignalStore.account().pniIdentityKey else IdentityKeyUtil.generateIdentityKeyPair()
    val deviceMessages = mutableListOf<OutgoingPushMessage>()
    val devicePniSignedPreKeys = mutableMapOf<Int, SignedPreKeyEntity>()
    val pniRegistrationIds = mutableMapOf<Int, Int>()
    val primaryDeviceId: Int = SignalServiceAddress.DEFAULT_DEVICE_ID

    val devices: List<Int> = listOf(primaryDeviceId) + aciProtocolStore.getSubDeviceSessions(selfIdentifier)

    devices
      .filter { it == primaryDeviceId || aciProtocolStore.containsSession(SignalProtocolAddress(selfIdentifier, it)) }
      .forEach { deviceId ->
        // Signed Prekeys
        val signedPreKeyRecord = if (deviceId == primaryDeviceId) {
          if (pniUpdateMode) {
            ApplicationDependencies.getProtocolStore().pni().loadSignedPreKey(SignalStore.account().pniPreKeys.activeSignedPreKeyId)
          } else {
            PreKeyUtil.generateAndStoreSignedPreKey(ApplicationDependencies.getProtocolStore().pni(), SignalStore.account().pniPreKeys, pniIdentity.privateKey)
          }
        } else {
          PreKeyUtil.generateSignedPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), pniIdentity.privateKey)
        }
        devicePniSignedPreKeys[deviceId] = SignedPreKeyEntity(signedPreKeyRecord.id, signedPreKeyRecord.keyPair.publicKey, signedPreKeyRecord.signature)

        // Registration Ids
        var pniRegistrationId = if (deviceId == primaryDeviceId && pniUpdateMode) {
          SignalStore.account().pniRegistrationId
        } else {
          -1
        }

        while (pniRegistrationId < 0 || pniRegistrationIds.values.contains(pniRegistrationId)) {
          pniRegistrationId = KeyHelper.generateRegistrationId(false)
        }
        pniRegistrationIds[deviceId] = pniRegistrationId

        // Device Messages
        if (deviceId != primaryDeviceId) {
          val pniChangeNumber = SyncMessage.PniChangeNumber.newBuilder()
            .setIdentityKeyPair(pniIdentity.serialize().toProtoByteString())
            .setSignedPreKey(signedPreKeyRecord.serialize().toProtoByteString())
            .setRegistrationId(pniRegistrationId)
            .build()

          deviceMessages += messageSender.getEncryptedSyncPniChangeNumberMessage(deviceId, pniChangeNumber)
        }
      }

    val request = ChangePhoneNumberRequest(
      newE164,
      code,
      registrationLock,
      pniIdentity.publicKey,
      deviceMessages,
      devicePniSignedPreKeys.mapKeys { it.key.toString() },
      pniRegistrationIds.mapKeys { it.key.toString() }
    )

    val metadata = PendingChangeNumberMetadata.newBuilder()
      .setPreviousPni(SignalStore.account().pni!!.toByteString())
      .setPniIdentityKeyPair(pniIdentity.serialize().toProtoByteString())
      .setPniRegistrationId(pniRegistrationIds[primaryDeviceId]!!)
      .setPniSignedPreKeyId(devicePniSignedPreKeys[primaryDeviceId]!!.keyId)
      .build()

    return ChangeNumberRequestData(request, metadata)
  }

  data class ChangeNumberRequestData(val changeNumberRequest: ChangePhoneNumberRequest, val pendingChangeNumberMetadata: PendingChangeNumberMetadata)
}
