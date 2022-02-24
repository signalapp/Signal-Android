package org.thoughtcrime.securesms.stories.viewer.reply.group

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.paging.PagedData
import org.signal.paging.PagingController
import org.thoughtcrime.securesms.conversation.colors.NameColors
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.Store

class StoryGroupReplyViewModel(storyId: Long, repository: StoryGroupReplyRepository) : ViewModel() {

  private val sessionMemberCache: MutableMap<GroupId, Set<Recipient>> = NameColors.createSessionMembersCache()
  private val store = Store(StoryGroupReplyState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryGroupReplyState> = store.stateLiveData

  private val pagedData: MutableLiveData<PagedData<StoryGroupReplyItemData.Key, StoryGroupReplyItemData>> = MutableLiveData()

  val pagingController: LiveData<PagingController<StoryGroupReplyItemData.Key>>
  val pageData: LiveData<List<StoryGroupReplyItemData>>

  init {
    disposables += repository.getPagedReplies(storyId).subscribe {
      pagedData.postValue(it)
    }

    pagingController = Transformations.map(pagedData) { it.controller }
    pageData = Transformations.switchMap(pagedData) { it.data }
    store.update(pageData) { data, state ->
      state.copy(
        noReplies = data.isEmpty(),
        loadState = StoryGroupReplyState.LoadState.READY
      )
    }

    disposables += repository.getStoryOwner(storyId).observeOn(AndroidSchedulers.mainThread()).subscribe { recipientId ->
      store.update(NameColors.getNameColorsMapLiveData(MutableLiveData(recipientId), sessionMemberCache)) { nameColors, state ->
        state.copy(nameColors = nameColors)
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val storyId: Long, private val repository: StoryGroupReplyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryGroupReplyViewModel(storyId, repository)) as T
    }
  }
}
