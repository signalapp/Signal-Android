package org.thoughtcrime.securesms.conversation.v2.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.session.libsession.utilities.Debouncer
import org.session.libsession.utilities.Util.runOnMain
import org.session.libsession.utilities.concurrent.SignalExecutors
import org.thoughtcrime.securesms.contacts.ContactAccessor
import org.thoughtcrime.securesms.database.CursorList
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.model.MessageResult
import org.thoughtcrime.securesms.util.CloseableLiveData
import java.io.Closeable


class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val searchRepository: SearchRepository
    private val result: CloseableLiveData<SearchResult>
    private val debouncer: Debouncer
    private var firstSearch = false
    private var searchOpen = false
    private var activeQuery: String? = null
    private var activeThreadId: Long = 0
    val searchResults: LiveData<SearchResult>
        get() = result

    fun onQueryUpdated(query: String, threadId: Long) {
        if (query == activeQuery) {
            return
        }
        updateQuery(query, threadId)
    }

    fun onMissingResult() {
        if (activeQuery != null) {
            updateQuery(activeQuery!!, activeThreadId)
        }
    }

    fun onMoveUp() {
        debouncer.clear()
        val messages = result.value!!.getResults() as CursorList<MessageResult?>
        val position = Math.min(result.value!!.position + 1, messages.size - 1)
        result.setValue(SearchResult(messages, position), false)
    }

    fun onMoveDown() {
        debouncer.clear()
        val messages = result.value!!.getResults() as CursorList<MessageResult?>
        val position = Math.max(result.value!!.position - 1, 0)
        result.setValue(SearchResult(messages, position), false)
    }

    fun onSearchOpened() {
        searchOpen = true
        firstSearch = true
    }

    fun onSearchClosed() {
        searchOpen = false
        activeQuery = null
        debouncer.clear()
        result.close()
    }

    override fun onCleared() {
        super.onCleared()
        result.close()
    }

    private fun updateQuery(query: String, threadId: Long) {
        activeQuery = query
        activeThreadId = threadId
        debouncer.publish {
            firstSearch = false
            searchRepository.query(query, threadId) { messages: CursorList<MessageResult?> ->
                runOnMain {
                    if (searchOpen && query == activeQuery) {
                        result.setValue(SearchResult(messages, 0))
                    } else {
                        messages.close()
                    }
                }
            }
        }
    }

    class SearchResult(private val results: CursorList<MessageResult?>, val position: Int) : Closeable {

        fun getResults(): List<MessageResult?> {
            return results
        }

        override fun close() {
            results.close()
        }
    }

    init {
        val context = application.applicationContext
        result = CloseableLiveData()
        debouncer = Debouncer(500)
        searchRepository = SearchRepository(context,
                DatabaseFactory.getSearchDatabase(context),
                DatabaseFactory.getThreadDatabase(context),
                ContactAccessor.getInstance(),
                SignalExecutors.SERIAL)
    }
}