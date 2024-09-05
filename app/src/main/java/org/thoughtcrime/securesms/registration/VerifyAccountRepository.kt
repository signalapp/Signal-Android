package org.thoughtcrime.securesms.registration

import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.kbs.MasterKey
import java.io.IOException

/**
 * Request SMS/Phone verification codes to help prove ownership of a phone number.
 */
class VerifyAccountRepository {

  fun interface MasterKeyProducer {
    @Throws(IOException::class, SvrWrongPinException::class, SvrNoDataException::class)
    fun produceMasterKey(): MasterKey
  }
}
