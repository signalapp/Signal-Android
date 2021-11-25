package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.SignalServiceAddress

/**
 * A list of Recipients, but with some helpful methods for retrieving them by various properties. Uses lazy properties to ensure that it will be as performant
 * as a regular list if you don't call any of the extra methods.
 */
class RecipientAccessList(private val recipients: List<Recipient>) : List<Recipient> by recipients {

  private val byAci: Map<ACI, Recipient> by lazy {
    recipients
      .filter { it.hasAci() }
      .associateBy { it.requireAci() }
  }

  private val byE164: Map<String, Recipient> by lazy {
    recipients
      .filter { it.hasE164() }
      .associateBy { it.requireE164() }
  }

  fun requireByAddress(address: SignalServiceAddress): Recipient {
    if (byAci.containsKey(address.aci)) {
      return byAci[address.aci]!!
    } else if (address.number.isPresent && byE164.containsKey(address.number.get())) {
      return byE164[address.number.get()]!!
    } else {
      throw IllegalArgumentException("Could not find a matching recipient!")
    }
  }

  fun requireIdByAddress(address: SignalServiceAddress): RecipientId {
    return requireByAddress(address).id
  }
}
