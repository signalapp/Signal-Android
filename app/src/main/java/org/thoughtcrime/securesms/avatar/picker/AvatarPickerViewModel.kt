package org.thoughtcrime.securesms.avatar.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.groups.GroupId

private val TAG = Log.tag(AvatarPickerViewModel::class.java)

sealed class AvatarPickerViewModel : EventDrivenViewModel<AvatarPickerEvents>(TAG, shouldLogEvents = false) {

  private val internalState = MutableStateFlow(AvatarPickerState())
  val state: StateFlow<AvatarPickerState> = internalState

  private val internalActions = Channel<AvatarPickerActions>(Channel.BUFFERED)
  val actions: Flow<AvatarPickerActions> = internalActions.receiveAsFlow()

  protected abstract suspend fun getAvatar(): Avatar
  protected abstract suspend fun getDefaultAvatarFromRepository(): Avatar
  protected abstract suspend fun getPersistedAvatars(): List<Avatar>
  protected abstract suspend fun getDefaultAvatars(): List<Avatar>
  protected abstract suspend fun persistAvatar(avatar: Avatar): Either<Throwable, Avatar>
  protected abstract suspend fun persistAndCreateMedia(avatar: Avatar): Either<Throwable, Media>

  override suspend fun processEvent(event: AvatarPickerEvents) {
    when (event) {
      AvatarPickerEvents.ClearAvatar -> clearAvatar()
      is AvatarPickerEvents.DeleteAvatar -> delete(event.avatar)
      is AvatarPickerEvents.AvatarEdited -> onAvatarEditCompleted(event.avatar)
      AvatarPickerEvents.Save -> save()
      is AvatarPickerEvents.AvatarSelected -> onAvatarSelectedFromGrid(event.avatar)
      is AvatarPickerEvents.PhotoSelected -> onAvatarPhotoSelectionCompleted(event.media)
      is AvatarPickerEvents.EditAvatar -> internalActions.send(AvatarPickerActions.LaunchAvatarEditor(event.avatar))
      AvatarPickerEvents.Close -> internalActions.send(AvatarPickerActions.Close)
      AvatarPickerEvents.CapturePhoto -> internalActions.send(AvatarPickerActions.LaunchCameraCapture)
      AvatarPickerEvents.SelectPhoto -> internalActions.send(AvatarPickerActions.LaunchPhotoSelection)
      AvatarPickerEvents.SelectText -> internalActions.send(AvatarPickerActions.LaunchTextAvatarCreation)
    }
  }

  private fun delete(avatar: Avatar) {
    viewModelScope.launch {
      AvatarPickerRepository.delete(avatar)
      refreshAvatar()
      refreshSelectableAvatars()
    }
  }

  private suspend fun clearAvatar() {
    val avatar = getDefaultAvatarFromRepository()

    internalState.update { it.copy(currentAvatar = avatar, canSave = true, canClear = false, isCleared = true) }
  }

  /** Saving closes the picker, so [AvatarPickerState.canSave] guards against doing it twice. */
  private suspend fun save() {
    if (!internalState.value.canSave) {
      return
    }

    internalState.update { it.copy(canSave = false) }

    if (internalState.value.isCleared) {
      internalActions.send(AvatarPickerActions.FinishWithClearedAvatar)
      return
    }

    val avatar = internalState.value.currentAvatar ?: throw AssertionError()

    persistAndCreateMedia(avatar)
      .onRight { internalActions.send(AvatarPickerActions.FinishWithAvatar(it)) }
      .onLeft {
        Log.w(TAG, "Failed to save avatar.", it)
        internalState.update { state -> state.copy(canSave = true) }
        internalActions.send(AvatarPickerActions.ShowSaveFailed)
      }
  }

  private fun onAvatarSelectedFromGrid(avatar: Avatar) {
    internalState.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = true, isCleared = false) }
  }

  private fun onAvatarEditCompleted(avatar: Avatar) {
    viewModelScope.launch {
      persistAvatar(avatar)
        .onRight { saved ->
          internalState.update { it.copy(currentAvatar = saved, canSave = isSaveable(saved), canClear = true, isCleared = false) }
          refreshSelectableAvatars()
        }
        .onLeft { Log.w(TAG, "Failed to persist edited avatar.", it) }
    }
  }

  private fun onAvatarPhotoSelectionCompleted(media: Media) {
    viewModelScope.launch {
      either {
        val multiSessionUri = AvatarPickerRepository.writeMediaToMultiSessionStorage(media).bind()
        persistAvatar(Avatar.Photo(multiSessionUri, media.size, Avatar.DatabaseId.NotSet)).bind()
      }
        .onRight { avatar ->
          internalState.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = true, isCleared = false) }
          refreshSelectableAvatars()
        }
        .onLeft { Log.w(TAG, "Failed to persist selected photo.", it) }
    }
  }

  protected fun refreshAvatar() {
    viewModelScope.launch {
      val avatar = getAvatar()
      internalState.update { it.copy(currentAvatar = avatar, canSave = isSaveable(avatar), canClear = avatar is Avatar.Photo && !isSaveable(avatar), isCleared = false) }
    }
  }

  protected fun refreshSelectableAvatars() {
    viewModelScope.launch {
      val custom = getPersistedAvatars()
      val default = getDefaultAvatars()
      val customKeys = custom.filterIsInstance<Avatar.Vector>().map { it.key }

      val avatars = custom + default.filterNot { it is Avatar.Vector && customKeys.contains(it.key) }

      internalState.update { it.copy(selectableAvatars = avatars) }
    }
  }

  private fun isSaveable(avatar: Avatar) = avatar.databaseId != Avatar.DatabaseId.DoNotPersist

  private class SelfAvatarPickerViewModel : AvatarPickerViewModel() {

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override suspend fun getAvatar(): Avatar = AvatarPickerRepository.getAvatarForSelf()
    override suspend fun getDefaultAvatarFromRepository(): Avatar = AvatarPickerRepository.getDefaultAvatarForSelf()
    override suspend fun getPersistedAvatars(): List<Avatar> = AvatarPickerRepository.getPersistedAvatarsForSelf()
    override suspend fun getDefaultAvatars(): List<Avatar> = AvatarPickerRepository.getDefaultAvatarsForSelf()

    override suspend fun persistAvatar(avatar: Avatar): Either<Throwable, Avatar> {
      return AvatarPickerRepository.persistAvatarForSelf(avatar)
    }

    override suspend fun persistAndCreateMedia(avatar: Avatar): Either<Throwable, Media> {
      return AvatarPickerRepository.persistAndCreateMediaForSelf(avatar)
    }
  }

  private class GroupAvatarPickerViewModel(
    private val groupId: GroupId,
    groupAvatarMedia: Media?
  ) : AvatarPickerViewModel() {

    private val initialAvatar: Avatar? = groupAvatarMedia?.let { Avatar.Photo(it.uri, it.size, Avatar.DatabaseId.DoNotPersist) }

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override suspend fun getAvatar(): Avatar {
      return initialAvatar ?: AvatarPickerRepository.getAvatarForGroup(groupId)
    }

    override suspend fun getDefaultAvatarFromRepository(): Avatar = AvatarPickerRepository.getDefaultAvatarForGroup(groupId)
    override suspend fun getPersistedAvatars(): List<Avatar> = AvatarPickerRepository.getPersistedAvatarsForGroup(groupId)
    override suspend fun getDefaultAvatars(): List<Avatar> = AvatarPickerRepository.getDefaultAvatarsForGroup()

    override suspend fun persistAvatar(avatar: Avatar): Either<Throwable, Avatar> {
      return AvatarPickerRepository.persistAvatarForGroup(avatar, groupId)
    }

    override suspend fun persistAndCreateMedia(avatar: Avatar): Either<Throwable, Media> {
      return AvatarPickerRepository.persistAndCreateMediaForGroup(avatar, groupId)
    }
  }

  private class NewGroupAvatarPickerViewModel(
    initialMedia: Media?
  ) : AvatarPickerViewModel() {

    private val initialAvatar: Avatar? = initialMedia?.let { Avatar.Photo(it.uri, it.size, Avatar.DatabaseId.DoNotPersist) }

    init {
      refreshAvatar()
      refreshSelectableAvatars()
    }

    override suspend fun getAvatar(): Avatar {
      return initialAvatar ?: getDefaultAvatarFromRepository()
    }

    override suspend fun getDefaultAvatarFromRepository(): Avatar = AvatarPickerRepository.getDefaultAvatarForGroup(null)
    override suspend fun getPersistedAvatars(): List<Avatar> = emptyList()
    override suspend fun getDefaultAvatars(): List<Avatar> = AvatarPickerRepository.getDefaultAvatarsForGroup()
    override suspend fun persistAvatar(avatar: Avatar): Either<Throwable, Avatar> = avatar.right()
    override suspend fun persistAndCreateMedia(avatar: Avatar): Either<Throwable, Media> = AvatarPickerRepository.createMediaForNewGroup(avatar)
  }

  class Factory(
    private val groupId: GroupId?,
    private val isNewGroup: Boolean,
    private val groupAvatarMedia: Media?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val viewModel = if (groupId == null && !isNewGroup) {
        SelfAvatarPickerViewModel()
      } else if (groupId == null) {
        NewGroupAvatarPickerViewModel(groupAvatarMedia)
      } else {
        GroupAvatarPickerViewModel(groupId, groupAvatarMedia)
      }

      return requireNotNull(modelClass.cast(viewModel))
    }
  }
}
