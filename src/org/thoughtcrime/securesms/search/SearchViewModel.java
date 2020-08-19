package org.thoughtcrime.securesms.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.database.ContentObserver;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.search.model.SearchResult;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.Util;

/**
 * A {@link ViewModel} for handling all the business logic and interactions that take place inside
 * of the {@link SearchFragment}.
 *
 * This class should be view- and Android-agnostic, and therefore should contain no references to
 * things like {@link android.content.Context}, {@link android.view.View},
 * {@link Fragment}, etc.
 */
class SearchViewModel extends ViewModel {

  private final ObservingLiveData searchResult;
  private final SearchRepository  searchRepository;
  private final Debouncer         debouncer;

  private String lastQuery;

  private SearchViewModel(@NonNull SearchRepository searchRepository) {
    this.searchResult     = new ObservingLiveData();
    this.searchRepository = searchRepository;
    this.debouncer        = new Debouncer(500);

    searchResult.registerContentObserver(new ContentObserver(new Handler()) {
      @Override
      public void onChange(boolean selfChange) {
        if (!TextUtils.isEmpty(getLastQuery())) {
          searchRepository.query(getLastQuery(), searchResult::postValue);
        }
      }
    });
  }

  LiveData<SearchResult> getSearchResult() {
    return searchResult;
  }

  void updateQuery(String query) {
    lastQuery = query;
    debouncer.publish(() -> searchRepository.query(query, result -> {
      Util.runOnMain(() -> {
        if (query.equals(lastQuery)) {
          searchResult.setValue(result);
        } else {
          result.close();
        }
      });
    }));
  }

  @NonNull
  String getLastQuery() {
    return lastQuery == null ? "" : lastQuery;
  }

  @Override
  protected void onCleared() {
    debouncer.clear();
    searchResult.close();
  }

  /**
   * Ensures that the previous {@link SearchResult} is always closed whenever we set a new one.
   */
  private static class ObservingLiveData extends MutableLiveData<SearchResult> {

    private ContentObserver observer;

    @Override
    public void setValue(SearchResult value) {
      SearchResult previous = getValue();

      if (previous != null) {
        previous.unregisterContentObserver(observer);
        previous.close();
      }

      value.registerContentObserver(observer);

      super.setValue(value);
    }

    void close() {
      SearchResult value = getValue();

      if (value != null) {
        value.unregisterContentObserver(observer);
        value.close();
      }
    }

    void registerContentObserver(@NonNull ContentObserver observer) {
      this.observer = observer;
    }
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final SearchRepository searchRepository;

    public Factory(@NonNull SearchRepository searchRepository) {
      this.searchRepository = searchRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new SearchViewModel(searchRepository));
    }
  }
}
