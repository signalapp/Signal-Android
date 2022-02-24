package org.thoughtcrime.securesms.stories.settings.my

data class MyStorySettingsState(
  val hiddenStoryFromCount: Int = 0,
  val areRepliesAndReactionsEnabled: Boolean = false
)
