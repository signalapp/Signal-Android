package org.thoughtcrime.securesms.stories.settings.story

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord

class StorySettingsRepository {
  fun getPrivateStories(): Single<List<DistributionListPartialRecord>> {
    return Single.fromCallable {
      SignalDatabase.distributionLists.getCustomListsForUi()
    }.subscribeOn(Schedulers.io())
  }
}
