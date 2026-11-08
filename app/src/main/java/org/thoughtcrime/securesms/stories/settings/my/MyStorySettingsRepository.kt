package org.thoughtcrime.securesms.stories.settings.my

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyData
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.settings.privacy.ChooseInitialMyStoryMembershipState

class MyStorySettingsRepository {

  fun observeChooseInitialPrivacy(): Flow<ChooseInitialMyStoryMembershipState> {
    return flow {
      val recipientId = SignalDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!!
      val allSignalConnectionsCount = readAllSignalConnectionsCount()

      emitAll(
        Recipient.observable(recipientId).asFlow().map {
          ChooseInitialMyStoryMembershipState(recipientId = recipientId, privacyState = readStoryPrivacyState(), allSignalConnectionsCount = allSignalConnectionsCount)
        }
      )
    }.flowOn(SignalDispatchers.Default)
  }

  suspend fun getPrivacyState(): MyStoryPrivacyState = withContext(SignalDispatchers.Default) {
    readStoryPrivacyState()
  }

  suspend fun getRepliesAndReactionsEnabled(): Boolean = withContext(SignalDispatchers.Default) {
    SignalDatabase.distributionLists.getStoryType(DistributionListId.MY_STORY).isStoryWithReplies
  }

  suspend fun getAllSignalConnectionsCount(): Int = withContext(SignalDispatchers.Default) {
    readAllSignalConnectionsCount()
  }

  suspend fun setMyStoryPrivacyMode(privacyMode: DistributionListPrivacyMode) = withContext(SignalDispatchers.Default) {
    SignalDatabase.distributionLists.setPrivacyMode(DistributionListId.MY_STORY, privacyMode)
    Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
  }

  suspend fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean) = withContext(SignalDispatchers.Default) {
    SignalDatabase.distributionLists.setAllowsReplies(DistributionListId.MY_STORY, repliesAndReactionsEnabled)
    Stories.onStorySettingsChanged(DistributionListId.MY_STORY)
  }

  suspend fun notifyStorySettingsChanged() = withContext(SignalDispatchers.Default) {
    Stories.onStorySettingsChanged(Recipient.self().id)
  }

  fun hasUserPerformedManualSelection(): Boolean {
    return SignalStore.story.userHasBeenNotifiedAboutStories
  }

  fun markUserPerformedManualSelection() {
    SignalStore.story.userHasBeenNotifiedAboutStories = true
  }

  @WorkerThread
  private fun readAllSignalConnectionsCount(): Int {
    return SignalDatabase.recipients.getSignalContactsCount(RecipientTable.IncludeSelfMode.Exclude)
  }

  @WorkerThread
  private fun readStoryPrivacyState(): MyStoryPrivacyState {
    val privacyData: DistributionListPrivacyData = SignalDatabase.distributionLists.getPrivacyData(DistributionListId.MY_STORY)

    return MyStoryPrivacyState(
      privacyMode = privacyData.privacyMode,
      connectionCount = privacyData.memberCount
    )
  }
}
