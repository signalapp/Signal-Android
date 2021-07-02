package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.lang.IllegalArgumentException
import java.util.UUID

/**
 * A list of Recipients, but with some helpful methods for retrieving them by various properties. Uses lazy properties to ensure that it will be as performant
 * as a regular list if you don't call any of the extra methods.
 */
class RecipientAccessList(private val recipients: List<Recipient>) : List<Recipient> by recipients {

  private val byUuid: Map<UUID, Recipient> by lazy {
    recipients
      .filter { it.hasUuid() }
      .associateBy { it.requireUuid() }
  }

  private val byE164: Map<String, Recipient> by lazy {
    recipients
      .filter { it.hasE164() }
      .associateBy { it.requireE164() }
  }

  fun requireByAddress(address: SignalServiceAddress): Recipient {
    if (address.uuid.isPresent && byUuid.containsKey(address.uuid.get())) {
      return byUuid.get(address.uuid.get())!!
    } else if (address.number.isPresent && byE164.containsKey(address.number.get())) {
      return byE164.get(address.number.get())!!
    } else {
      throw IllegalArgumentException("Could not find a matching recipient!")
    }
  }

  fun requireIdByAddress(address: SignalServiceAddress): RecipientId {
    return requireByAddress(address).id
  }
}
