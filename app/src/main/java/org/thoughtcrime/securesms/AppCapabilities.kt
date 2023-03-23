package org.thoughtcrime.securesms

import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.account.AccountAttributes

object AppCapabilities {
  /**
   * @param storageCapable Whether or not the user can use storage service. This is another way of
   * asking if the user has set a Signal PIN or not.
   */
  @JvmStatic
  fun getCapabilities(storageCapable: Boolean): AccountAttributes.Capabilities {
    return AccountAttributes.Capabilities(
      uuid = false,
      gv2 = true,
      storage = storageCapable,
      gv1Migration = true,
      senderKey = true,
      announcementGroup = true,
      changeNumber = true,
      stories = true,
      giftBadges = true,
      pni = FeatureFlags.phoneNumberPrivacy(),
      paymentActivation = true
    )
  }
}
