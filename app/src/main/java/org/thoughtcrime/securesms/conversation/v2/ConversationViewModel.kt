package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import java.util.UUID

class ConversationViewModel(
    val threadId: Long,
    private val repository: ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState

    val recipient: Recipient
        get() = repository.getRecipientForThreadId(threadId)

    init {
        _uiState.update {
            it.copy(isOxenHostedOpenGroup = repository.isOxenHostedOpenGroup(threadId))
        }
    }

    fun saveDraft(text: String) {
        repository.saveDraft(threadId, text)
    }

    fun getDraft(): String? {
        return repository.getDraft(threadId)
    }

    fun inviteContacts(contacts: List<Recipient>) {
        repository.inviteContacts(threadId, contacts)
    }

    fun unblock() {
        if (recipient.isContactRecipient) {
            repository.unblock(recipient)
        }
    }

    fun deleteLocally(message: MessageRecord) {
        repository.deleteLocally(recipient, message)
    }

    fun deleteForEveryone(message: MessageRecord) = viewModelScope.launch {
        repository.deleteForEveryone(threadId, recipient, message)
            .onFailure {
                showMessage("Couldn't delete message due to error: $it")
            }
    }

    fun deleteMessagesWithoutUnsendRequest(messages: Set<MessageRecord>) = viewModelScope.launch {
        repository.deleteMessageWithoutUnsendRequest(threadId, messages)
            .onFailure {
                showMessage("Couldn't delete message due to error: $it")
            }
    }

    fun banUser(recipient: Recipient) = viewModelScope.launch {
        repository.banUser(threadId, recipient)
            .onSuccess {
                showMessage("Successfully banned user")
            }
            .onFailure {
                showMessage("Couldn't ban user due to error: $it")
            }
    }

    fun banAndDeleteAll(recipient: Recipient) = viewModelScope.launch {
        repository.banAndDeleteAll(threadId, recipient)
            .onSuccess {
                showMessage("Successfully banned user and deleted all their messages")
            }
            .onFailure {
                showMessage("Couldn't execute request due to error: $it")
            }
    }

    fun acceptMessageRequest() = viewModelScope.launch {
        repository.acceptMessageRequest(threadId, recipient)
            .onSuccess {
                _uiState.update {
                    it.copy(isMessageRequestAccepted = true)
                }
            }
            .onFailure {
                showMessage("Couldn't accept message request due to error: $it")
            }
    }

    fun declineMessageRequest() {
        repository.declineMessageRequest(threadId, recipient)
    }

    private fun showMessage(message: String) {
        _uiState.update { currentUiState ->
            val messages = currentUiState.uiMessages + UiMessage(
                id = UUID.randomUUID().mostSignificantBits,
                message = message
            )
            currentUiState.copy(uiMessages = messages)
        }
    }
    
    fun messageShown(messageId: Long) {
        _uiState.update { currentUiState ->
            val messages = currentUiState.uiMessages.filterNot { it.id == messageId }
            currentUiState.copy(uiMessages = messages)
        }
    }

    fun hasReceived(): Boolean {
        return repository.hasReceived(threadId)
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val repository: ConversationRepository
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationViewModel(threadId, repository) as T
        }
    }
}

data class UiMessage(val id: Long, val message: String)

data class ConversationUiState(
    val isOxenHostedOpenGroup: Boolean = false,
    val uiMessages: List<UiMessage> = emptyList(),
    val isMessageRequestAccepted: Boolean? = null
)
