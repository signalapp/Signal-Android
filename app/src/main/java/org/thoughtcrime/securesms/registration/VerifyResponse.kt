package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.api.account.PreKeyCollection
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

data class VerifyResponse(
  val verifyAccountResponse: VerifyAccountResponse,
  val masterKey: MasterKey?,
  val pin: String?,
  val aciPreKeyCollection: PreKeyCollection?,
  val pniPreKeyCollection: PreKeyCollection?
) {
  companion object {
    fun from(
      response: ServiceResponse<VerifyAccountResponse>,
      masterKey: MasterKey?,
      pin: String?,
      aciPreKeyCollection: PreKeyCollection?,
      pniPreKeyCollection: PreKeyCollection?
    ): ServiceResponse<VerifyResponse> {
      return if (response.result.isPresent) {
        ServiceResponse.forResult(VerifyResponse(response.result.get(), masterKey, pin, aciPreKeyCollection, pniPreKeyCollection), 200, null)
      } else {
        ServiceResponse.coerceError(response)
      }
    }
  }
}
