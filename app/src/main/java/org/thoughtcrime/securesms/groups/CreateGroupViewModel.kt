package org.thoughtcrime.securesms.groups

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.ThreadDatabase
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val threadDb: ThreadDatabase,
    private val textSecurePreferences: TextSecurePreferences
) : ViewModel() {

    private val _recipients = MutableLiveData<List<Recipient>>()
    val recipients: LiveData<List<Recipient>> = _recipients

    init {
        viewModelScope.launch {
            threadDb.approvedConversationList.use { openCursor ->
                val reader = threadDb.readerFor(openCursor)
                val recipients = mutableListOf<Recipient>()
                while (true) {
                    recipients += reader.next?.recipient ?: break
                }
                withContext(Dispatchers.Main) {
                    _recipients.value = recipients
                        .filter { !it.isGroupRecipient && it.hasApprovedMe() && it.address.serialize() != textSecurePreferences.getLocalNumber() }
                }
            }
        }
    }

    fun filter(query: String): List<Recipient> {
        return _recipients.value?.filter {
            it.address.serialize().contains(query, ignoreCase = true) || it.name?.contains(query, ignoreCase = true) == true
        } ?: emptyList()
    }
}