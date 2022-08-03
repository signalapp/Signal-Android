package org.thoughtcrime.securesms.testing

import io.reactivex.rxjava3.core.Single
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.signal.core.util.Hex
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.pin.KbsRepository
import org.thoughtcrime.securesms.pin.TokenData
import org.thoughtcrime.securesms.test.BuildConfig
import org.whispersystems.signalservice.api.KbsPinData
import org.whispersystems.signalservice.api.KeyBackupService
import org.whispersystems.signalservice.api.kbs.HashedPin
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.DeviceInfoList
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.SenderCertificate
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.security.SecureRandom

/**
 * Warehouse of reusable test data and mock configurations.
 */
object MockProvider {

  val senderCertificate = SenderCertificate().apply { certificate = ByteArray(0) }

  val lockedFailure = PushServiceSocket.RegistrationLockFailure().apply {
    backupCredentials = AuthCredentials.create("username", "password")
  }

  val primaryOnlyDeviceList = DeviceInfoList().apply {
    devices = listOf(
      DeviceInfo().apply {
        id = 1
      }
    )
  }

  fun createVerifyAccountResponse(aci: ServiceId, newPni: ServiceId): VerifyAccountResponse {
    return VerifyAccountResponse().apply {
      uuid = aci.toString()
      pni = newPni.toString()
      storageCapable = false
    }
  }

  fun createWhoAmIResponse(aci: ServiceId, pni: ServiceId, e164: String): WhoAmIResponse {
    return WhoAmIResponse().apply {
      this.uuid = aci.toString()
      this.pni = pni.toString()
      this.number = e164
    }
  }

  fun mockGetRegistrationLockStringFlow(kbsRepository: KbsRepository) {
    val tokenData: TokenData = mock {
      on { enclave } doReturn BuildConfig.KBS_ENCLAVE
      on { basicAuth } doReturn "basicAuth"
      on { triesRemaining } doReturn 10
      on { tokenResponse } doReturn TokenResponse()
    }

    kbsRepository.stub {
      on { getToken(any() as String) } doReturn Single.just(ServiceResponse.forResult(tokenData, 200, ""))
    }

    val session: KeyBackupService.RestoreSession = object : KeyBackupService.RestoreSession {
      override fun hashSalt(): ByteArray = Hex.fromStringCondensed("cba811749042b303a6a7efa5ccd160aea5e3ea243c8d2692bd13d515732f51a8")
      override fun restorePin(hashedPin: HashedPin?): KbsPinData = KbsPinData(MasterKey.createNew(SecureRandom()), null)
    }

    val kbsService = ApplicationDependencies.getKeyBackupService(BuildConfig.KBS_ENCLAVE)
    kbsService.stub {
      on { newRegistrationSession(any(), any()) } doReturn session
    }
  }
}
