package org.thoughtcrime.securesms.avatar.picker

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.util.livedata.Store

sealed class AvatarPickerViewModel(private val repository: AvatarPickerRepository) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val store = Store(AvatarPickerState())

  val state: LiveData<AvatarPickerState> = store.stateLiveData

  protected abstract fun getAvatar(): Single<Avatar>
  protected abstract fun getDefaultAvatarFromRepository(): Avatar
  protected abstract fun getPersistedAvatars(): Single<List<Avatar>>
  protected abstract fun getDefaultAvatars(): Single<List<Avatar>>
  protected abstract fun persistAvatar(avatar: Avatar, onPersisted: (Avatar) -> Unit)
  protected abstract fun persistAndCreateMedia(avatar: Avatar, onSaved: (Media) -> Unit)

  fun delete(avatar: Avatar) {
    repository.delete(avatar) {
      refreshAvatar()
      refreshSelectableAvatars()
    }
  }

  fun clearAvatar() {
    store.update {
      val avatar = getDefaultAvatarFromRepository()
      it.copy(currentAvatar = avatar, canSave = true, canClear = false, isCleared = true)
    }
  }

  fun save(onSaved: (Media) -> Unit, onCleared: () -> Unit) {
    if (store.state.isCleared) {
      onCleared()
    } else {
      val avatar = store.state.currentAvatar ?: throw AssertionError()
      persistAndCreateMedia(avatar, onSaved)
    }
  }

  fun onAvatarSelectedFromGrid(avatar: Avatar) {
    store.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = true, isCleared = false) }
  }

  fun onAvatarEditCompleted(avatar: Avatar) {
    persistAvatar(avatar) { saved ->
      store.update { it.copy(currentAvatar = saved, canSave = isSaveable(saved), canClear = true, isCleared = false) }
      refreshSelectableAvatars()
    }
  }

  fun onAvatarPhotoSelectionCompleted(media: Media) {
    repository.writeMediaToMultiSessionStorage(media) { multiSessionUri ->
      persistAvatar(Avatar.Photo(multiSessionUri, media.size, Avatar.DatabaseId.NotSet)) { avatar ->
        store.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = true, isCleared = false) }
        refreshSelectableAvatars()
      }
    }
  }

  protected fun refreshAvatar() {
    disposables.add(
      getAvatar().subscribeOn(Schedulers.io()).subscribe { avatar ->
        store.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = avatar is Avatar.Photo && !isSaveable(avatar), isCleared = false) }
      }
    )
  }

  protected fun refreshSelectableAvatars() {
    disposables.add(
      Single.zip(getPersistedAvatars(), getDefaultAvatars()) { custom, def ->
        val customKeys = custom.filterIsInstance(Avatar.Vector::class.java).map { it.key }
        custom + def.filterNot {
          it is Avatar.Vector && customKeys.contains(it.key)
        }
      }.subscribeOn(Schedulers.io()).subscribe { avatars ->
        store.update { it.copy(selectableAvatars = avatars) }
      }
    )
  }

  private fun isSaveable(avatar: Avatar) = avatar.databaseId != Avatar.DatabaseId.DoNotPersist

  override fun onCleared() {
    disposables.dispose()
  }

  private class SelfAvatarPickerViewModel(private val repository: AvatarPickerRepository) : AvatarPickerViewModel(repository) {

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override fun getAvatar(): Single<Avatar> = repository.getAvatarForSelf()
    override fun getDefaultAvatarFromRepository(): Avatar = repository.getDefaultAvatarForSelf()
    override fun getPersistedAvatars(): Single<List<Avatar>> = repository.getPersistedAvatarsForSelf()
    override fun getDefaultAvatars(): Single<List<Avatar>> = repository.getDefaultAvatarsForSelf()

    override fun persistAvatar(avatar: Avatar, onPersisted: (Avatar) -> Unit) {
      repository.persistAvatarForSelf(avatar, onPersisted)
    }

    override fun persistAndCreateMedia(avatar: Avatar, onSaved: (Media) -> Unit) {
      repository.persistAndCreateMediaForSelf(avatar, onSaved)
    }
  }

  private class GroupAvatarPickerViewModel(
    private val groupId: GroupId,
    private val repository: AvatarPickerRepository,
    groupAvatarMedia: Media?
  ) : AvatarPickerViewModel(repository) {

    private val initialAvatar: Avatar? = groupAvatarMedia?.let { Avatar.Photo(it.uri, it.size, Avatar.DatabaseId.DoNotPersist) }

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override fun getAvatar(): Single<Avatar> {
      return if (initialAvatar != null) {
        Single.just(initialAvatar)
      } else {
        repository.getAvatarForGroup(groupId)
      }
    }

    override fun getDefaultAvatarFromRepository(): Avatar = repository.getDefaultAvatarForGroup(groupId)
    override fun getPersistedAvatars(): Single<List<Avatar>> = repository.getPersistedAvatarsForGroup(groupId)
    override fun getDefaultAvatars(): Single<List<Avatar>> = repository.getDefaultAvatarsForGroup()

    override fun persistAvatar(avatar: Avatar, onPersisted: (Avatar) -> Unit) {
      repository.persistAvatarForGroup(avatar, groupId, onPersisted)
    }

    override fun persistAndCreateMedia(avatar: Avatar, onSaved: (Media) -> Unit) {
      repository.persistAndCreateMediaForGroup(avatar, groupId, onSaved)
    }
  }

  private class NewGroupAvatarPickerViewModel(
    private val repository: AvatarPickerRepository,
    initialMedia: Media?
  ) : AvatarPickerViewModel(repository) {

    private val initialAvatar: Avatar? = initialMedia?.let { Avatar.Photo(it.uri, it.size, Avatar.DatabaseId.DoNotPersist) }

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override fun getAvatar(): Single<Avatar> {
      return if (initialAvatar != null) {
        Single.just(initialAvatar)
      } else {
        Single.fromCallable { getDefaultAvatarFromRepository() }
      }
    }

    override fun getDefaultAvatarFromRepository(): Avatar = repository.getDefaultAvatarForGroup(null)
    override fun getPersistedAvatars(): Single<List<Avatar>> = Single.just(listOf())
    override fun getDefaultAvatars(): Single<List<Avatar>> = repository.getDefaultAvatarsForGroup()
    override fun persistAvatar(avatar: Avatar, onPersisted: (Avatar) -> Unit) = onPersisted(avatar)
    override fun persistAndCreateMedia(avatar: Avatar, onSaved: (Media) -> Unit) = repository.createMediaForNewGroup(avatar, onSaved)
  }

  class Factory(
    private val repository: AvatarPickerRepository,
    private val groupId: GroupId?,
    private val isNewGroup: Boolean,
    private val groupAvatarMedia: Media?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val viewModel = if (groupId == null && !isNewGroup) {
        SelfAvatarPickerViewModel(repository)
      } else if (groupId == null) {
        NewGroupAvatarPickerViewModel(repository, groupAvatarMedia)
      } else {
        GroupAvatarPickerViewModel(groupId, repository, groupAvatarMedia)
      }

      return requireNotNull(modelClass.cast(viewModel))
    }
  }
}
