/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.nicknames

import androidx.annotation.MainThread
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class NicknameViewModel(
  private val recipientId: RecipientId
) : ViewModel() {
  companion object {
    private const val NAME_MAX_LENGTH = 26
    private const val NOTE_MAX_LENGTH = 240
  }

  private val internalState = mutableStateOf(NicknameState())
  private val iteratorCompat = BreakIteratorCompat.getInstance()

  val state: MutableState<NicknameState> = internalState

  private val recipientDisposable = Recipient.observable(recipientId)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe { recipient ->
      internalState.value = if (state.value.formState == NicknameState.FormState.LOADING) {
        val noteLength = iteratorCompat.run {
          setText(recipient.note ?: "")
          countBreaks()
        }

        NicknameState(
          recipient = recipient,
          firstName = recipient.nickname.givenName,
          lastName = recipient.nickname.familyName,
          note = recipient.note ?: "",
          noteCharactersRemaining = NOTE_MAX_LENGTH - noteLength,
          formState = NicknameState.FormState.READY,
          hasBecomeReady = true,
          isEditing = !recipient.nickname.isEmpty || recipient.note?.isNotNullOrBlank() == true
        )
      } else {
        state.value.copy(recipient = recipient)
      }
    }

  override fun onCleared() {
    recipientDisposable.dispose()
  }

  @MainThread
  fun onFirstNameChanged(value: String) {
    iteratorCompat.setText(value)
    internalState.value = state.value.copy(firstName = iteratorCompat.take(NAME_MAX_LENGTH).toString())
  }

  @MainThread
  fun onLastNameChanged(value: String) {
    iteratorCompat.setText(value)
    internalState.value = state.value.copy(lastName = iteratorCompat.take(NAME_MAX_LENGTH).toString())
  }

  @MainThread
  fun onNoteChanged(value: String) {
    if (internalState.value.noteCharactersRemaining == 0 && value.graphemeCount > NOTE_MAX_LENGTH) {
      return
    }

    iteratorCompat.setText(value)
    val trimmed = iteratorCompat.take(NOTE_MAX_LENGTH)
    val count = trimmed.graphemeCount

    internalState.value = state.value.copy(
      note = trimmed.toString(),
      noteCharactersRemaining = NOTE_MAX_LENGTH - count
    )
  }

  @MainThread
  fun delete() {
    viewModelScope.launch {
      internalState.value = state.value.copy(formState = NicknameState.FormState.SAVING)

      withContext(Dispatchers.IO) {
        SignalDatabase.recipients.setNicknameAndNote(
          recipientId,
          ProfileName.EMPTY,
          ""
        )
      }

      internalState.value = state.value.copy(formState = NicknameState.FormState.SAVED)
    }
  }

  @MainThread
  fun save() {
    viewModelScope.launch {
      val stateSnapshot = state.value.copy(formState = NicknameState.FormState.SAVING)
      internalState.value = stateSnapshot

      withContext(Dispatchers.IO) {
        SignalDatabase.recipients.setNicknameAndNote(
          recipientId,
          ProfileName.fromParts(stateSnapshot.firstName, stateSnapshot.lastName),
          stateSnapshot.note
        )
      }

      internalState.value = state.value.copy(formState = NicknameState.FormState.SAVED)
    }
  }

  private val CharSequence.graphemeCount: Int
    get() {
      iteratorCompat.setText(this)
      return iteratorCompat.countBreaks()
    }
}
