package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.api.KbsPinData
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

data class VerifyResponse(val verifyAccountResponse: VerifyAccountResponse, val kbsData: KbsPinData?, val pin: String?) {
  companion object {
    fun from(response: ServiceResponse<VerifyAccountResponse>, kbsData: KbsPinData?, pin: String?): ServiceResponse<VerifyResponse> {
      return if (response.result.isPresent) {
        ServiceResponse.forResult(VerifyResponse(response.result.get(), kbsData, pin), 200, null)
      } else {
        ServiceResponse.coerceError(response)
      }
    }
  }
}
