package org.thoughtcrime.securesms.migrations

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Added to initialize whether the user has seen the onboarding story
 */
internal class StoryReadStateMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "StoryReadStateMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!SignalStore.storyValues().hasUserOnboardingStoryReadBeenSet()) {
      SignalStore.storyValues().userHasReadOnboardingStory = SignalStore.storyValues().userHasReadOnboardingStory
      SignalDatabase.messages.markOnboardingStoryRead()

      if (SignalStore.account().isRegistered) {
        recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<StoryReadStateMigrationJob> {
    override fun create(parameters: Parameters, data: Data): StoryReadStateMigrationJob {
      return StoryReadStateMigrationJob(parameters)
    }
  }
}
