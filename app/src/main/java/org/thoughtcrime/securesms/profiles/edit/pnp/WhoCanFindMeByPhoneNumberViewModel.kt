package org.thoughtcrime.securesms.profiles.edit.pnp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.rx3.await

class WhoCanFindMeByPhoneNumberViewModel : ViewModel() {

  private val repository = WhoCanFindMeByPhoneNumberRepository()

  private val internalState = MutableStateFlow(repository.getCurrentState())
  val state: StateFlow<WhoCanFindMeByPhoneNumberState> = internalState.asStateFlow()

  fun onEveryoneCanFindMeByPhoneNumberSelected() {
    internalState.update { WhoCanFindMeByPhoneNumberState.EVERYONE }
  }

  fun onNobodyCanFindMeByPhoneNumberSelected() {
    internalState.update { WhoCanFindMeByPhoneNumberState.NOBODY }
  }

  suspend fun onSave() {
    repository.onSave(internalState.value).await()
  }
}
