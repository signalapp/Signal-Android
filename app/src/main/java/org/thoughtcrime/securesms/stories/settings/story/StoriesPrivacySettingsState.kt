package org.thoughtcrime.securesms.stories.settings.story

import org.thoughtcrime.securesms.contacts.paged.ContactSearchData

data class StoriesPrivacySettingsState(
  val areStoriesEnabled: Boolean,
  val storyContactItems: List<ContactSearchData> = emptyList()
)
