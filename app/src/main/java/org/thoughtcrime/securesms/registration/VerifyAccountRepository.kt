package org.thoughtcrime.securesms.registration

import android.app.Application
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.AppCapabilities
import org.thoughtcrime.securesms.gcm.FcmUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.KeyBackupSystemWrongPinException
import org.thoughtcrime.securesms.push.AccountManagerFactory
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.KbsPinData
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import java.io.IOException
import java.util.Locale
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Request SMS/Phone verification codes to help prove ownership of a phone number.
 */
class VerifyAccountRepository(private val context: Application) {

  fun requestVerificationCode(
    e164: String,
    password: String,
    mode: Mode,
    captchaToken: String? = null
  ): Single<ServiceResponse<RequestVerificationCodeResponse>> {
    Log.d(TAG, "SMS Verification requested")

    return Single.fromCallable {
      val fcmToken: Optional<String> = FcmUtil.getToken(context)
      val accountManager = AccountManagerFactory.createUnauthenticated(context, e164, SignalServiceAddress.DEFAULT_DEVICE_ID, password)
      val pushChallenge = PushChallengeRequest.getPushChallengeBlocking(accountManager, fcmToken, e164, PUSH_REQUEST_TIMEOUT)

      if (mode == Mode.PHONE_CALL) {
        accountManager.requestVoiceVerificationCode(Locale.getDefault(), Optional.ofNullable(captchaToken), pushChallenge, fcmToken)
      } else {
        accountManager.requestSmsVerificationCode(Locale.getDefault(), mode.isSmsRetrieverSupported, Optional.ofNullable(captchaToken), pushChallenge, fcmToken)
      }
    }.subscribeOn(Schedulers.io())
  }

  fun verifyAccount(registrationData: RegistrationData): Single<ServiceResponse<VerifyResponse>> {
    val universalUnidentifiedAccess: Boolean = TextSecurePreferences.isUniversalUnidentifiedAccess(context)
    val unidentifiedAccessKey: ByteArray = UnidentifiedAccess.deriveAccessKeyFrom(registrationData.profileKey)

    val accountManager: SignalServiceAccountManager = AccountManagerFactory.createUnauthenticated(
      context,
      registrationData.e164,
      SignalServiceAddress.DEFAULT_DEVICE_ID,
      registrationData.password
    )

    return Single.fromCallable {
      val response = accountManager.verifyAccount(
        registrationData.code,
        registrationData.registrationId,
        registrationData.isNotFcm,
        unidentifiedAccessKey,
        universalUnidentifiedAccess,
        AppCapabilities.getCapabilities(true),
        SignalStore.phoneNumberPrivacy().phoneNumberListingMode.isDiscoverable,
        registrationData.pniRegistrationId
      )
      VerifyResponse.from(response, null, null)
    }.subscribeOn(Schedulers.io())
  }

  fun verifyAccountWithPin(registrationData: RegistrationData, pin: String, kbsPinDataProducer: KbsPinDataProducer): Single<ServiceResponse<VerifyResponse>> {
    val universalUnidentifiedAccess: Boolean = TextSecurePreferences.isUniversalUnidentifiedAccess(context)
    val unidentifiedAccessKey: ByteArray = UnidentifiedAccess.deriveAccessKeyFrom(registrationData.profileKey)

    val accountManager: SignalServiceAccountManager = AccountManagerFactory.createUnauthenticated(
      context,
      registrationData.e164,
      SignalServiceAddress.DEFAULT_DEVICE_ID,
      registrationData.password
    )

    return Single.fromCallable {
      try {
        val kbsData = kbsPinDataProducer.produceKbsPinData()
        val registrationLockV2: String = kbsData.masterKey.deriveRegistrationLock()

        val response: ServiceResponse<VerifyAccountResponse> = accountManager.verifyAccountWithRegistrationLockPin(
          registrationData.code,
          registrationData.registrationId,
          registrationData.isNotFcm,
          registrationLockV2,
          unidentifiedAccessKey,
          universalUnidentifiedAccess,
          AppCapabilities.getCapabilities(true),
          SignalStore.phoneNumberPrivacy().phoneNumberListingMode.isDiscoverable,
          registrationData.pniRegistrationId
        )
        VerifyResponse.from(response, kbsData, pin)
      } catch (e: KeyBackupSystemWrongPinException) {
        ServiceResponse.forExecutionError(e)
      } catch (e: KeyBackupSystemNoDataException) {
        ServiceResponse.forExecutionError(e)
      } catch (e: IOException) {
        ServiceResponse.forExecutionError(e)
      }
    }.subscribeOn(Schedulers.io())
  }

  interface KbsPinDataProducer {
    @Throws(IOException::class, KeyBackupSystemWrongPinException::class, KeyBackupSystemNoDataException::class)
    fun produceKbsPinData(): KbsPinData
  }

  enum class Mode(val isSmsRetrieverSupported: Boolean) {
    SMS_WITH_LISTENER(true),
    SMS_WITHOUT_LISTENER(false),
    PHONE_CALL(false);
  }

  companion object {
    private val TAG = Log.tag(VerifyAccountRepository::class.java)
    private val PUSH_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(5)
  }
}
