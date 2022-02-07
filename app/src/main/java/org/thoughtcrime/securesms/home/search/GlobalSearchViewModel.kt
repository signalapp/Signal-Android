package org.thoughtcrime.securesms.home.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.session.libsignal.utilities.SettableFuture
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.model.SearchResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(private val searchRepository: SearchRepository) : ViewModel() {

    private val executor = viewModelScope + SupervisorJob()

    private val _result: MutableStateFlow<GlobalSearchResult> =
            MutableStateFlow(GlobalSearchResult.EMPTY)

    val result: StateFlow<GlobalSearchResult> = _result

    private val _queryText: MutableStateFlow<CharSequence> = MutableStateFlow("")

    fun postQuery(charSequence: CharSequence?) {
        charSequence ?: return
        _queryText.value = charSequence
    }

    init {
        //
        _queryText
                .buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .mapLatest { query ->
                    if (query.trim().length < 2) {
                        SearchResult.EMPTY
                    } else {
                        // user input delay here in case we get a new query within a few hundred ms
                        // this coroutine will be cancelled and expensive query will not be run if typing quickly
                        // first query of 2 characters will be instant however
                        delay(300)
                        val settableFuture = SettableFuture<SearchResult>()
                        searchRepository.query(query.toString(), settableFuture::set)
                        try {
                            // search repository doesn't play nicely with suspend functions (yet)
                            settableFuture.get(10_000, TimeUnit.MILLISECONDS)
                        } catch (e: Exception) {
                            SearchResult.EMPTY
                        }
                    }
                }
                .onEach { result ->
                    // update the latest _result value
                    _result.value = GlobalSearchResult.from(result)
                }
                .launchIn(executor)
    }


}