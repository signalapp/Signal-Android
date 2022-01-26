package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.content.Context
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
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
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.io.IOException
import java.security.MessageDigest

private val TAG: String = Log.tag(ChangeNumberRepository::class.java)

class ChangeNumberRepository(private val context: Context) {

  private val accountManager = ApplicationDependencies.getSignalServiceAccountManager()

  fun changeNumber(code: String, newE164: String): Single<ServiceResponse<VerifyAccountResponse>> {
    return Single.fromCallable { accountManager.changeNumber(code, newE164, null) }
      .subscribeOn(Schedulers.io())
  }

  fun changeNumber(
    code: String,
    newE164: String,
    pin: String,
    tokenData: TokenData
  ): Single<ServiceResponse<VerifyAccountRepository.VerifyAccountWithRegistrationLockResponse>> {
    return Single.fromCallable {
      try {
        val kbsData: KbsPinData = KbsRepository.restoreMasterKey(pin, tokenData.enclave, tokenData.basicAuth, tokenData.tokenResponse)!!
        val registrationLock: String = kbsData.masterKey.deriveRegistrationLock()

        val response: ServiceResponse<VerifyAccountResponse> = accountManager.changeNumber(code, newE164, registrationLock)
        VerifyAccountRepository.VerifyAccountWithRegistrationLockResponse.from(response, kbsData)
      } catch (e: KeyBackupSystemWrongPinException) {
        ServiceResponse.forExecutionError(e)
      } catch (e: KeyBackupSystemNoDataException) {
        ServiceResponse.forExecutionError(e)
      } catch (e: IOException) {
        ServiceResponse.forExecutionError(e)
      }
    }.subscribeOn(Schedulers.io())
  }

  @Suppress("UsePropertyAccessSyntax")
  fun whoAmI(): Single<WhoAmIResponse> {
    return Single.fromCallable { ApplicationDependencies.getSignalServiceAccountManager().getWhoAmI() }
      .subscribeOn(Schedulers.io())
  }

  @WorkerThread
  fun changeLocalNumber(e164: String, pni: PNI): Single<Unit> {
    val oldStorageId: ByteArray? = Recipient.self().storageServiceId
    SignalDatabase.recipients.updateSelfPhone(e164)
    val newStorageId: ByteArray? = Recipient.self().storageServiceId

    if (MessageDigest.isEqual(oldStorageId, newStorageId)) {
      Log.w(TAG, "Self storage id was not rotated, attempting to rotate again")
      SignalDatabase.recipients.rotateStorageId(Recipient.self().id)
      Recipient.self().live().refresh()
      StorageSyncHelper.scheduleSyncForDataChange()
      val secondAttemptStorageId: ByteArray? = Recipient.self().storageServiceId
      if (MessageDigest.isEqual(oldStorageId, secondAttemptStorageId)) {
        Log.w(TAG, "Second attempt also failed to rotate storage id")
      }
    }

    SignalDatabase.recipients.setPni(Recipient.self().id, pni)

    SignalStore.account().setE164(e164)
    SignalStore.account().setPni(pni)

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
    }.subscribeOn(Schedulers.io())
  }
}
