package org.thoughtcrime.securesms.stories.settings.my

import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

data class MyStoryPrivacyState(val privacyMode: DistributionListPrivacyMode? = null, val connectionCount: Int = 0)
