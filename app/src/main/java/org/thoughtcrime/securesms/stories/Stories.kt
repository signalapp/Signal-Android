package org.thoughtcrime.securesms.stories

import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.FeatureFlags

object Stories {
  @JvmStatic
  fun isFeatureAvailable(): Boolean {
    return FeatureFlags.stories() && Recipient.self().storiesCapability == Recipient.Capability.SUPPORTED
  }

  @JvmStatic
  fun isFeatureEnabled(): Boolean {
    return isFeatureAvailable() && !SignalStore.storyValues().isFeatureDisabled
  }
}
