package org.thoughtcrime.securesms.conversationlist;

import android.app.Application;
import android.database.ContentObserver;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.megaphone.Megaphone;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.search.SearchRepository;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.paging.Invalidator;

class ConversationListViewModel extends ViewModel {

  private final Application                   application;
  private final MutableLiveData<Megaphone>    megaphone;
  private final MutableLiveData<SearchResult> searchResult;
  private final MutableLiveData<Integer>      archivedCount;
  private final LiveData<ConversationList>    conversationList;
  private final SearchRepository              searchRepository;
  private final MegaphoneRepository           megaphoneRepository;
  private final Debouncer                     debouncer;
  private final ContentObserver               observer;
  private final Invalidator                   invalidator;

  private String lastQuery;

  private ConversationListViewModel(@NonNull Application application, @NonNull SearchRepository searchRepository, boolean isArchived) {
    this.application         = application;
    this.megaphone           = new MutableLiveData<>();
    this.searchResult        = new MutableLiveData<>();
    this.archivedCount       = new MutableLiveData<>();
    this.searchRepository    = searchRepository;
    this.megaphoneRepository = ApplicationDependencies.getMegaphoneRepository();
    this.debouncer           = new Debouncer(300);
    this.invalidator         = new Invalidator();
    this.observer            = new ContentObserver(new Handler()) {
      @Override
      public void onChange(boolean selfChange) {
        if (!TextUtils.isEmpty(getLastQuery())) {
          searchRepository.query(getLastQuery(), searchResult::postValue);
        }

        if (!isArchived) {
          updateArchivedCount();
        }
      }
    };

    DataSource.Factory<Integer, Conversation> factory = new ConversationListDataSource.Factory(application, invalidator, isArchived);
    PagedList.Config                          config  = new PagedList.Config.Builder()
                                                                            .setPageSize(15)
                                                                            .setInitialLoadSizeHint(30)
                                                                            .setEnablePlaceholders(true)
                                                                            .build();

    LiveData<PagedList<Conversation>> conversationList = new LivePagedListBuilder<>(factory, config).setFetchExecutor(ConversationListDataSource.EXECUTOR)
                                                                                                    .setInitialLoadKey(0)
                                                                                                    .build();

    if (isArchived) {
      this.archivedCount.setValue(0);
    } else {
      updateArchivedCount();
    }

    application.getContentResolver().registerContentObserver(DatabaseContentProviders.ConversationList.CONTENT_URI, true, observer);

    this.conversationList = LiveDataUtil.combineLatest(conversationList, this.archivedCount, ConversationList::new);
  }

  @NonNull LiveData<SearchResult> getSearchResult() {
    return searchResult;
  }

  @NonNull LiveData<Megaphone> getMegaphone() {
    return megaphone;
  }

  @NonNull LiveData<ConversationList> getConversationList() {
    return conversationList;
  }

  void onVisible() {
    megaphoneRepository.getNextMegaphone(megaphone::postValue);
  }

  void onMegaphoneCompleted(@NonNull Megaphones.Event event) {
    megaphone.postValue(null);
    megaphoneRepository.markFinished(event);
  }

  void onMegaphoneSnoozed(@NonNull Megaphones.Event event) {
    megaphoneRepository.markSeen(event);
    megaphone.postValue(null);
  }

  void onMegaphoneVisible(@NonNull Megaphone visible) {
    megaphoneRepository.markVisible(visible.getEvent());
  }

  void updateQuery(String query) {
    lastQuery = query;
    debouncer.publish(() -> searchRepository.query(query, result -> {
      Util.runOnMain(() -> {
        if (query.equals(lastQuery)) {
          searchResult.setValue(result);
        }
      });
    }));
  }

  private @NonNull String getLastQuery() {
    return lastQuery == null ? "" : lastQuery;
  }

  @Override
  protected void onCleared() {
    invalidator.invalidate();
    debouncer.clear();
    application.getContentResolver().unregisterContentObserver(observer);
  }

  private void updateArchivedCount() {
    SignalExecutors.BOUNDED.execute(() -> {
      archivedCount.postValue(DatabaseFactory.getThreadDatabase(application).getArchivedConversationListCount());
    });
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final boolean isArchived;

    public Factory(boolean isArchived) {
      this.isArchived = isArchived;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationListViewModel(ApplicationDependencies.getApplication(), new SearchRepository(), isArchived));
    }
  }

  final static class ConversationList {
    private final PagedList<Conversation> conversations;
    private final int                     archivedCount;

    ConversationList(PagedList<Conversation> conversations, int archivedCount) {
      this.conversations = conversations;
      this.archivedCount = archivedCount;
    }

    PagedList<Conversation> getConversations() {
      return conversations;
    }

    int getArchivedCount() {
      return archivedCount;
    }

    boolean isEmpty() {
      return conversations.isEmpty() && archivedCount == 0;
    }
  }
}
