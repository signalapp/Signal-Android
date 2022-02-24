package org.thoughtcrime.securesms.stories.settings.my

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId

class MyStorySettingsRepository {

  fun getHiddenRecipientCount(): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.distributionLists.getRawMemberCount(DistributionListId.from(DistributionListId.MY_STORY_ID))
    }.subscribeOn(Schedulers.io())
  }
}
