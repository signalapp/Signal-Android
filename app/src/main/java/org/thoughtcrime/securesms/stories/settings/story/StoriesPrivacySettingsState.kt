package org.thoughtcrime.securesms.stories.settings.story

import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.stories.archive.StoryArchiveDuration

data class StoriesPrivacySettingsState(
  val areStoriesEnabled: Boolean,
  val areViewReceiptsEnabled: Boolean,
  val isUpdatingEnabledState: Boolean = false,
  val storyContactItems: List<ContactSearchData> = emptyList(),
  val userHasStories: Boolean = false,
  val isArchiveEnabled: Boolean = false,
  val archiveDuration: StoryArchiveDuration = StoryArchiveDuration.THIRTY_DAYS
)
