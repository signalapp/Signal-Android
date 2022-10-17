package org.thoughtcrime.securesms.stories.settings.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.LiveGroup
import org.thoughtcrime.securesms.groups.ParcelableGroupId
import org.thoughtcrime.securesms.util.livedata.Store

/**
 * This class utilizes LiveData due to pre-existing infrastructure in LiveGroup
 */
class GroupStorySettingsViewModel(private val groupId: GroupId) : ViewModel() {

  private val repository = GroupStorySettingsRepository()
  private val store = Store(GroupStorySettingsState())

  val state: LiveData<GroupStorySettingsState> = store.stateLiveData
  val titleSnapshot: String get() = store.state.name

  init {
    val group = LiveGroup(groupId)

    store.update(group.fullMembers) { members, state -> state.copy(members = members.map { it.member }) }
    store.update(group.title) { title, state -> state.copy(name = title) }
  }

  fun doNotDisplayAsStory() {
    repository.unmarkAsGroupStory(groupId).subscribe {
      store.update { it.copy(removed = true) }
    }
  }

  fun getConversationData(): Single<GroupConversationData> {
    return repository.getConversationData(groupId).observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val parcelableGroupId: ParcelableGroupId) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(GroupStorySettingsViewModel(ParcelableGroupId.get(parcelableGroupId)!!)) as T
    }
  }
}
