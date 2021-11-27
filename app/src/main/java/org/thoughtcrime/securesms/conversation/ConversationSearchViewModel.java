package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.search.SearchRepository;
import org.thoughtcrime.securesms.util.Debouncer;

import java.util.Collections;
import java.util.List;

public class ConversationSearchViewModel extends AndroidViewModel {

  private final SearchRepository              searchRepository;
  private final MutableLiveData<SearchResult> result;
  private final Debouncer                     debouncer;

  private boolean firstSearch;
  private boolean searchOpen;
  private String  activeQuery;
  private long    activeThreadId;

  public ConversationSearchViewModel(@NonNull Application application) {
    super(application);
    result           = new MutableLiveData<>();
    debouncer        = new Debouncer(500);
    searchRepository = new SearchRepository();
  }

  LiveData<SearchResult> getSearchResults() {
    return result;
  }

  void onQueryUpdated(@NonNull String query, long threadId, boolean forced) {
    if (firstSearch && query.length() < 2) {
      result.postValue(new SearchResult(Collections.emptyList(), 0));
      return;
    }

    if (query.equals(activeQuery) && !forced) {
      return;
    }

    updateQuery(query, threadId);
  }

  void onMissingResult() {
    if (activeQuery != null) {
      updateQuery(activeQuery, activeThreadId);
    }
  }

  void onMoveUp() {
    if (result.getValue() == null) {
      return;
    }

    debouncer.clear();

    List<MessageResult> messages = result.getValue().getResults();
    int                 position = Math.min(result.getValue().getPosition() + 1, messages.size() - 1);

    result.setValue(new SearchResult(messages, position));
  }

  void onMoveDown() {
    if (result.getValue() == null) {
      return;
    }

    debouncer.clear();

    List<MessageResult> messages = result.getValue().getResults();
    int                 position = Math.max(result.getValue().getPosition() - 1, 0);

    result.setValue(new SearchResult(messages, position));
  }


  void onSearchOpened() {
    searchOpen  = true;
    firstSearch = true;
  }

  void onSearchClosed() {
    searchOpen = false;
    debouncer.clear();
  }

  private void updateQuery(@NonNull String query, long threadId) {
    activeQuery    = query;
    activeThreadId = threadId;

    debouncer.publish(() -> {
      firstSearch = false;

      searchRepository.query(query, threadId, messages -> {
        ThreadUtil.runOnMain(() -> {
          if (searchOpen && query.equals(activeQuery)) {
            result.setValue(new SearchResult(messages, 0));
          }
        });
      });
    });
  }

  static class SearchResult {

    private final List<MessageResult> results;
    private final int                 position;

    SearchResult(@NonNull List<MessageResult> results, int position) {
      this.results  = results;
      this.position = position;
    }

    public List<MessageResult> getResults() {
      return results;
    }

    public int getPosition() {
      return position;
    }
  }
}
