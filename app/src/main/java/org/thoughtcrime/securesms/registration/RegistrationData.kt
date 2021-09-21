package org.thoughtcrime.securesms.registration

import org.signal.zkgroup.profiles.ProfileKey

data class RegistrationData(
  val code: String,
  val e164: String,
  val password: String,
  val registrationId: Int,
  val profileKey: ProfileKey,
  val fcmToken: String?
) {
  val isFcm: Boolean = fcmToken != null
  val isNotFcm: Boolean = fcmToken == null
}
