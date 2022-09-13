package org.thoughtcrime.securesms.stories.settings.story

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.Stories

class StoriesPrivacySettingsRepository {
  fun markGroupsAsStories(groups: List<RecipientId>): Completable {
    return Completable.fromCallable {
      groups
        .map { Recipient.resolved(it) }
        .forEach { SignalDatabase.groups.markDisplayAsStory(it.requireGroupId()) }
    }
  }

  fun setStoriesEnabled(isEnabled: Boolean): Completable {
    return Completable.fromAction {
      SignalStore.storyValues().isFeatureDisabled = !isEnabled
      Stories.onStorySettingsChanged(Recipient.self().id)
    }.subscribeOn(Schedulers.io())
  }
}
