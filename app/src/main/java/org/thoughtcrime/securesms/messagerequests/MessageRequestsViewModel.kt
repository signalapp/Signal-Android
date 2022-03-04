package org.thoughtcrime.securesms.messagerequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import javax.inject.Inject

@HiltViewModel
class MessageRequestsViewModel @Inject constructor(
    private val repository: ConversationRepository
) : ViewModel() {

    fun deleteMessageRequest(thread: ThreadRecord) = viewModelScope.launch {
        repository.deleteMessageRequest(thread)
    }

    fun clearAllMessageRequests() = viewModelScope.launch {
        repository.clearAllMessageRequests()
    }

}
