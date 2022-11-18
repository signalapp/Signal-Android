package org.thoughtcrime.securesms.conversationlist;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.signal.paging.LivePagedData;
import org.signal.paging.PagedData;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet;
import org.thoughtcrime.securesms.conversationlist.model.UnreadPayments;
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.megaphone.Megaphone;
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.payments.UnreadPaymentsRepository;
import org.thoughtcrime.securesms.search.SearchRepository;
import org.thoughtcrime.securesms.search.SearchResult;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.paging.Invalidator;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Pair;

class ConversationListViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationListViewModel.class);

  private static boolean coldStart = true;

  private final MutableLiveData<Megaphone>                  megaphone;
  private final MutableLiveData<SearchResult>               searchResult;
  private final MutableLiveData<ConversationSet>            selectedConversations;
  private final MutableLiveData<ConversationFilter>         conversationFilter;
  private final LiveData<ConversationListDataSource>        conversationListDataSource;
  private final Set<Conversation>                           internalSelection;
  private final LiveData<LivePagedData<Long, Conversation>> pagedData;
  private final LiveData<Boolean>                           hasNoConversations;
  private final SearchRepository                            searchRepository;
  private final MegaphoneRepository                         megaphoneRepository;
  private final Debouncer                                   messageSearchDebouncer;
  private final Debouncer                                   contactSearchDebouncer;
  private final ThrottledDebouncer                          updateDebouncer;
  private final DatabaseObserver.Observer                   observer;
  private final Invalidator                                 invalidator;
  private final CompositeDisposable                         disposables;
  private final UnreadPaymentsLiveData                      unreadPaymentsLiveData;
  private final UnreadPaymentsRepository                    unreadPaymentsRepository;
  private final NotificationProfilesRepository              notificationProfilesRepository;

  private String                  activeQuery;
  private SearchResult            activeSearchResult;
  private int                     pinnedCount;
  private ConversationFilterLatch conversationFilterLatch;

  private ConversationListViewModel(@NonNull SearchRepository searchRepository, boolean isArchived) {
    this.megaphone                      = new MutableLiveData<>();
    this.searchResult                   = new MutableLiveData<>();
    this.internalSelection              = new HashSet<>();
    this.selectedConversations          = new MutableLiveData<>(new ConversationSet());
    this.searchRepository               = searchRepository;
    this.megaphoneRepository            = ApplicationDependencies.getMegaphoneRepository();
    this.unreadPaymentsRepository       = new UnreadPaymentsRepository();
    this.notificationProfilesRepository = new NotificationProfilesRepository();
    this.messageSearchDebouncer         = new Debouncer(500);
    this.contactSearchDebouncer         = new Debouncer(100);
    this.updateDebouncer                = new ThrottledDebouncer(500);
    this.activeSearchResult             = SearchResult.EMPTY;
    this.invalidator                    = new Invalidator();
    this.disposables                    = new CompositeDisposable();
    this.conversationFilter             = new MutableLiveData<>(ConversationFilter.OFF);
    this.conversationFilterLatch        = ConversationFilterLatch.RESET;
    this.conversationListDataSource     = Transformations.map(conversationFilter, filter -> ConversationListDataSource.create(filter, isArchived));
    this.pagedData                      = Transformations.map(conversationListDataSource, source -> PagedData.createForLiveData(source,
                                                                                                                                new PagingConfig.Builder()
                                                                                                                                    .setPageSize(15)
                                                                                                                                    .setBufferPages(2)
                                                                                                                                    .build()));
    this.unreadPaymentsLiveData         = new UnreadPaymentsLiveData();
    this.observer                       = () -> {
      updateDebouncer.publish(() -> {
        if (!TextUtils.isEmpty(activeQuery)) {
          onSearchQueryUpdated(activeQuery);
        }

        LivePagedData<Long, Conversation> data = pagedData.getValue();
        if (data == null) {
          return;
        }

        data.getController().onDataInvalidated();
      });
    };

    this.hasNoConversations = LiveDataUtil.mapAsync(LiveDataUtil.combineLatest(conversationFilter, getConversationList(), Pair::new), filterAndData -> {
      pinnedCount = SignalDatabase.threads().getPinnedConversationListCount(ConversationFilter.OFF);

      if (filterAndData.getSecond().size() > 0) {
        return false;
      } else {
        return SignalDatabase.threads().getArchivedConversationListCount(filterAndData.getFirst()) == 0;
      }
    });

    ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer);
  }

  public LiveData<Boolean> hasNoConversations() {
    return hasNoConversations;
  }

  @NonNull LiveData<SearchResult> getSearchResult() {
    return searchResult;
  }

  @NonNull LiveData<Megaphone> getMegaphone() {
    return megaphone;
  }

  @NonNull LiveData<List<Conversation>> getConversationList() {
    return Transformations.switchMap(pagedData, LivePagedData::getData);
  }

  @NonNull LiveData<PagingController<Long>> getPagingController() {
    return Transformations.map(pagedData, LivePagedData::getController);
  }

  @NonNull LiveData<List<NotificationProfile>> getNotificationProfiles() {
    final Observable<List<NotificationProfile>> activeProfile = Observable.combineLatest(Observable.interval(0, 30, TimeUnit.SECONDS), notificationProfilesRepository.getProfiles(), (interval, profiles) -> profiles);

    return LiveDataReactiveStreams.fromPublisher(activeProfile.toFlowable(BackpressureStrategy.LATEST));
  }

  @NonNull LiveData<WebSocketConnectionState> getPipeState() {
    return LiveDataReactiveStreams.fromPublisher(ApplicationDependencies.getSignalWebSocket().getWebSocketState().toFlowable(BackpressureStrategy.LATEST));
  }

  @NonNull LiveData<Optional<UnreadPayments>> getUnreadPaymentsLiveData() {
    return unreadPaymentsLiveData;
  }

  public int getPinnedCount() {
    return pinnedCount;
  }

  void onVisible() {
    megaphoneRepository.getNextMegaphone(megaphone::postValue);

    if (!coldStart) {
      ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
    }

    coldStart = false;
  }

  @NonNull Set<Conversation> currentSelectedConversations() {
    return internalSelection;
  }

  @NonNull LiveData<ConversationSet> getSelectedConversations() {
    return selectedConversations;
  }

  void startSelection(@NonNull Conversation conversation) {
    setSelection(Collections.singleton(conversation));
  }

  void endSelection() {
    setSelection(Collections.emptySet());
  }

  void toggleConversationSelected(@NonNull Conversation conversation) {
    Set<Conversation> newSelection = new HashSet<>(internalSelection);
    if (newSelection.contains(conversation)) {
      newSelection.remove(conversation);
    } else {
      newSelection.add(conversation);
    }

    setSelection(newSelection);
  }

  void setConversationFilterLatch(@NonNull ConversationFilterLatch latch) {
    ConversationFilterLatch previous = conversationFilterLatch;
    conversationFilterLatch = latch;
    if (previous != latch && latch == ConversationFilterLatch.RESET) {
      toggleUnreadChatsFilter();
    }
  }

  public void toggleUnreadChatsFilter() {
    ConversationFilter filter = Objects.requireNonNull(conversationFilter.getValue());
    if (filter == ConversationFilter.UNREAD) {
      Log.d(TAG, "Setting filter to OFF");
      conversationFilter.setValue(ConversationFilter.OFF);
    } else {
      Log.d(TAG, "Setting filter to UNREAD");
      conversationFilter.setValue(ConversationFilter.UNREAD);
    }
  }

  private void setSelection(@NonNull Collection<Conversation> newSelection) {
    internalSelection.clear();
    internalSelection.addAll(newSelection);
    selectedConversations.setValue(new ConversationSet(internalSelection));
  }

  void onSelectAllClick() {
    ConversationListDataSource dataSource = conversationListDataSource.getValue();
    if (dataSource == null) {
      return;
    }

    disposables.add(
        Single.fromCallable(() -> dataSource.load(0, dataSource.size(), disposables::isDisposed))
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(this::setSelection)
    );
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

  void onUnreadPaymentsClosed() {
    unreadPaymentsRepository.markAllPaymentsSeen();
  }

  void onSearchQueryUpdated(String query) {
    activeQuery = query;

    contactSearchDebouncer.publish(() -> {
      searchRepository.queryThreads(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });

      searchRepository.queryContacts(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });
    });

    messageSearchDebouncer.publish(() -> {
      searchRepository.queryMessages(query, result -> {
        if (!result.getQuery().equals(activeQuery)) {
          return;
        }

        if (!activeSearchResult.getQuery().equals(activeQuery)) {
          activeSearchResult = SearchResult.EMPTY;
        }

        activeSearchResult = activeSearchResult.merge(result);
        searchResult.postValue(activeSearchResult);
      });
    });
  }

  @Override
  protected void onCleared() {
    invalidator.invalidate();
    disposables.dispose();
    messageSearchDebouncer.clear();
    updateDebouncer.clear();
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final boolean isArchived;
    private final String  noteToSelfTitle;

    public Factory(boolean isArchived, @NonNull String noteToSelfTitle) {
      this.isArchived      = isArchived;
      this.noteToSelfTitle = noteToSelfTitle;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationListViewModel(new SearchRepository(noteToSelfTitle), isArchived));
    }
  }
}
