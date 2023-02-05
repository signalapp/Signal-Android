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
      isUuid = false,
      isGv2 = true,
      isStorage = storageCapable,
      isGv1Migration = true,
      isSenderKey = true,
      isAnnouncementGroup = true,
      isChangeNumber = true,
      isStories = true,
      isGiftBadges = true,
      isPnp = FeatureFlags.phoneNumberPrivacy(),
      paymentActivation = true
    )
  }
}
