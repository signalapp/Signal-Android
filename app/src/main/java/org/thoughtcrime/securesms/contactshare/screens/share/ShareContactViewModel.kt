/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.recipients.RecipientId

/** A loaded contact alongside the state derived from it. */
data class LoadedContact(
  val contact: Contact,
  val state: ShareContactState,
  val photoOptions: List<ShareContactState.PhotoOption> = emptyList()
)

/** The user's choices, resolved into what should end up on the wire. */
data class ShareContactSelection(
  val name: ContactNameParts?,
  val displayName: String,
  val photo: ShareContactState.ContactPhoto?,
  val detailIds: Set<String>
)

class ShareContactViewModel(
  private val contactSource: SharedContactSource?,
  private val recipientId: RecipientId?,
  private val repository: ShareContactRepository,
  private val savedState: SavedStateHandle
) : EventDrivenViewModel<ShareContactEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(ShareContactViewModel::class)

    private const val KEY_AVATAR_SELECTED = "avatar_selected"
    private const val KEY_NAME_SELECTED = "name_selected"
    private const val KEY_PHOTO_ID = "photo_id"
    private const val KEY_DETAIL_IDS = "detail_ids"
    private const val KEY_NAME_PARTS = "name_parts"
  }

  private val _state = MutableStateFlow(ShareContactState(isLoading = true))
  val state: StateFlow<ShareContactState> = _state.asStateFlow()

  private var source: Contact? = null

  /** Backs the name row. Not part of rendered state, since only the display name is shown. */
  private var nameParts: ContactNameParts = ContactNameParts()

  private var photoOptions: List<ShareContactState.PhotoOption> = emptyList()

  private val _actions = Channel<ShareContactAction>(Channel.BUFFERED)

  val actions: Flow<ShareContactAction> = _actions.receiveAsFlow()

  init {
    onEvent(ShareContactEvent.Initialize)

    // The card is rebuilt from scratch after process death, so the user's choices are saved
    // separately and reapplied once it reloads.
    viewModelScope.launch {
      _state.collect { persistSelection(it) }
    }
  }

  override suspend fun processEvent(event: ShareContactEvent) {
    when (event) {
      ShareContactEvent.Initialize -> {
        val loaded = contactSource?.let { repository.load(it, recipientId) }

        if (loaded == null) {
          Log.w(TAG, "Could not read a contact to share.")
          _actions.send(ShareContactAction.InvalidContact)
          return
        }

        source = loaded.contact
        photoOptions = loaded.photoOptions
        nameParts = savedState.get<ContactNameParts>(KEY_NAME_PARTS) ?: loaded.contact.toNameParts()
        _state.value = loaded.state.withSavedSelection()
      }

      ShareContactEvent.AvatarToggled -> {
        _state.update { current ->
          current.copy(avatar = current.avatar?.let { it.copy(isSelected = !it.isSelected) })
        }
      }

      ShareContactEvent.NameToggled -> {
        _state.update { current ->
          val name = current.name
          if (name == null || !name.isToggleable) {
            current
          } else {
            current.copy(name = name.copy(isSelected = !name.isSelected))
          }
        }
      }

      is ShareContactEvent.DetailToggled -> {
        _state.update { current ->
          current.copy(
            details = current.details.map { detail ->
              if (detail.id == event.id) detail.copy(isSelected = !detail.isSelected) else detail
            }
          )
        }
      }

      ShareContactEvent.EditNameClicked -> _actions.send(ShareContactAction.EditName(nameParts))

      is ShareContactEvent.NameEdited -> {
        nameParts = event.parts
        _state.update { current ->
          current.copy(name = current.name?.copy(displayName = event.parts.toDisplayName()))
        }
      }

      ShareContactEvent.EditPhotoClicked -> {
        val selectedId = photoOptions.firstOrNull { it.photo == _state.value.avatar?.photo }?.id

        if (photoOptions.size < 2 || selectedId == null) {
          Log.d(TAG, "Nothing to choose between. Ignoring photo edit.")
          return
        }

        _state.update { it.copy(photoPicker = ShareContactState.PhotoPicker(options = photoOptions, selectedId = selectedId)) }
      }

      is ShareContactEvent.PhotoSelected -> {
        _state.update { current ->
          current.copy(photoPicker = current.photoPicker?.copy(selectedId = event.id))
        }
      }

      ShareContactEvent.PhotoPickerConfirmed -> {
        _state.update { current ->
          val chosen = current.photoPicker?.let { picker ->
            picker.options.firstOrNull { it.id == picker.selectedId }
          }

          current.copy(
            avatar = if (chosen != null) current.avatar?.copy(photo = chosen.photo) else current.avatar,
            photoPicker = null
          )
        }
      }

      ShareContactEvent.PhotoPickerDismissed -> {
        _state.update { it.copy(photoPicker = null) }
      }

      ShareContactEvent.SendClicked -> {
        val current = _state.value
        if (!current.canSend) {
          Log.w(TAG, "Send requested without a selected name. Ignoring.")
          return
        }

        val contact = source

        if (contact == null) {
          Log.w(TAG, "Send with no loaded contact.")
          return
        }

        _state.update { it.copy(isSending = true) }

        _actions.send(ShareContactAction.Send(repository.buildCard(contact, current.toSelection(nameParts))))
      }

      ShareContactEvent.BackClicked -> _actions.send(ShareContactAction.Exit)
    }
  }

  private fun persistSelection(state: ShareContactState) {
    if (state.isLoading) {
      return
    }

    savedState[KEY_AVATAR_SELECTED] = state.avatar?.isSelected
    savedState[KEY_NAME_SELECTED] = state.name?.isSelected
    savedState[KEY_PHOTO_ID] = photoOptions.firstOrNull { it.photo == state.avatar?.photo }?.id
    savedState[KEY_DETAIL_IDS] = ArrayList(state.details.filter { it.isSelected }.map { it.id })
    savedState[KEY_NAME_PARTS] = nameParts
  }

  /** Returns the state untouched when nothing was saved, which is the normal first load. */
  private fun ShareContactState.withSavedSelection(): ShareContactState {
    val detailIds: List<String> = savedState.get<ArrayList<String>>(KEY_DETAIL_IDS) ?: return this
    // Resolved to an option rather than to a photo, so that a saved "no photo" is not mistaken for nothing saved.
    val savedOption = savedState.get<String>(KEY_PHOTO_ID)?.let { id -> photoOptions.firstOrNull { it.id == id } }

    return copy(
      avatar = avatar?.copy(
        isSelected = savedState.get<Boolean>(KEY_AVATAR_SELECTED) ?: avatar.isSelected,
        photo = if (savedOption != null) savedOption.photo else avatar.photo
      ),
      name = name?.copy(
        isSelected = savedState.get<Boolean>(KEY_NAME_SELECTED) ?: name.isSelected,
        displayName = nameParts.toDisplayName().ifBlank { name.displayName }
      ),
      details = details.map { it.copy(isSelected = it.id in detailIds) }
    )
  }
}

private fun Contact.toNameParts(): ContactNameParts {
  return ContactNameParts(
    prefix = this.name.prefix.orEmpty(),
    givenName = this.name.givenName.orEmpty(),
    middleName = this.name.middleName.orEmpty(),
    familyName = this.name.familyName.orEmpty(),
    suffix = this.name.suffix.orEmpty(),
    organization = this.organization.orEmpty()
  )
}

private fun ShareContactState.toSelection(nameParts: ContactNameParts): ShareContactSelection {
  val selectedName = name?.takeIf { it.isSelected }

  return ShareContactSelection(
    name = nameParts.takeIf { selectedName != null },
    displayName = selectedName?.displayName ?: "",
    photo = avatar?.takeIf { it.isSelected }?.photo,
    detailIds = details.filter { it.isSelected }.map { it.id }.toSet()
  )
}

/** Mirrors ContactUtil.getDisplayName, where the company is a fallback rather than a suffix. */
private fun ContactNameParts.toDisplayName(): String {
  val joined = listOf(prefix, givenName, middleName, familyName, suffix)
    .filter { it.isNotBlank() }
    .joinToString(" ")

  return joined.ifBlank { organization }
}
