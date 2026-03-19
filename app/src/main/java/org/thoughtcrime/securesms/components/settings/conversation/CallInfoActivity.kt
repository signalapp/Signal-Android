package org.thoughtcrime.securesms.components.settings.conversation

import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

class CallInfoActivity : ConversationSettingsActivity(), ConversationSettingsFragment.TransitionCallback {

  override val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
}
