package org.thoughtcrime.securesms.conversation.start

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.ThreadDatabase
import javax.inject.Inject

@HiltViewModel
class NewConversationHomeViewModel @Inject constructor(private val threadDb: ThreadDatabase): ViewModel() {

    private val _recipients = MutableLiveData<List<Recipient>>()
    val recipients: LiveData<List<Recipient>> = _recipients

    init {
        viewModelScope.launch {
            threadDb.approvedConversationList.use { openCursor ->
                val reader = threadDb.readerFor(openCursor)
                val threads = mutableListOf<Recipient>()
                while (true) {
                    threads += reader.next?.recipient ?: break
                }
                withContext(Dispatchers.Main) {
                    _recipients.value = threads
                }
            }
        }
    }
}
