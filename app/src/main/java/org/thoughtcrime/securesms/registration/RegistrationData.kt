package org.thoughtcrime.securesms.registration

import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.account.PreKeyCollection

data class RegistrationData(
  val code: String,
  val e164: String,
  val password: String,
  val registrationId: Int,
  val profileKey: ProfileKey,
  val aciPreKeyCollection: PreKeyCollection,
  val pniPreKeyCollection: PreKeyCollection,
  val fcmToken: String?,
  val pniRegistrationId: Int,
  val recoveryPassword: String?
) {
  val isNotFcm: Boolean = fcmToken.isNullOrBlank()
  val isFcm: Boolean = !isNotFcm
}
