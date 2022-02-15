package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.MapUtil;
import org.signal.core.util.logging.Log;
import org.signal.paging.PagedData;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import org.signal.paging.ProxyPagingController;
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette;
import org.thoughtcrime.securesms.conversation.colors.NameColor;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaRepository;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles;
import org.thoughtcrime.securesms.ratelimit.RecaptchaRequiredEvent;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.livedata.Store;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Observable;

public class ConversationViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationViewModel.class);

  private final Application                         context;
  private final MediaRepository                     mediaRepository;
  private final ConversationRepository              conversationRepository;
  private final MutableLiveData<List<Media>>        recentMedia;
  private final MutableLiveData<Long>               threadId;
  private final LiveData<List<ConversationMessage>> messages;
  private final LiveData<ConversationData>          conversationMetadata;
  private final MutableLiveData<Boolean>            showScrollButtons;
  private final MutableLiveData<Boolean>            hasUnreadMentions;
  private final LiveData<Boolean>                   canShowAsBubble;
  private final ProxyPagingController<MessageId>    pagingController;
  private final DatabaseObserver.Observer           conversationObserver;
  private final DatabaseObserver.MessageObserver    messageUpdateObserver;
  private final DatabaseObserver.MessageObserver    messageInsertObserver;
  private final MutableLiveData<RecipientId>        recipientId;
  private final LiveData<ChatWallpaper>             wallpaper;
  private final SingleLiveEvent<Event>              events;
  private final LiveData<ChatColors>                chatColors;
  private final MutableLiveData<Integer>            toolbarBottom;
  private final MutableLiveData<Integer>            inlinePlayerHeight;
  private final LiveData<Integer>                   conversationTopMargin;
  private final Store<ThreadAnimationState>         threadAnimationStateStore;
  private final Observer<ThreadAnimationState>      threadAnimationStateStoreDriver;
  private final NotificationProfilesRepository      notificationProfilesRepository;
  private final MutableLiveData<String>             searchQuery;

  private final Map<GroupId, Set<Recipient>> sessionMemberCache = new HashMap<>();

  private ConversationIntents.Args args;
  private int                      jumpToPosition;

  private ConversationViewModel() {
    this.context                        = ApplicationDependencies.getApplication();
    this.mediaRepository                = new MediaRepository();
    this.conversationRepository         = new ConversationRepository();
    this.recentMedia                    = new MutableLiveData<>();
    this.threadId                       = new MutableLiveData<>();
    this.showScrollButtons              = new MutableLiveData<>(false);
    this.hasUnreadMentions              = new MutableLiveData<>(false);
    this.recipientId                    = new MutableLiveData<>();
    this.events                         = new SingleLiveEvent<>();
    this.pagingController               = new ProxyPagingController<>();
    this.conversationObserver           = pagingController::onDataInvalidated;
    this.messageUpdateObserver          = pagingController::onDataItemChanged;
    this.messageInsertObserver          = messageId -> pagingController.onDataItemInserted(messageId, 0);
    this.toolbarBottom                  = new MutableLiveData<>();
    this.inlinePlayerHeight             = new MutableLiveData<>();
    this.conversationTopMargin          = Transformations.distinctUntilChanged(LiveDataUtil.combineLatest(toolbarBottom, inlinePlayerHeight, Integer::sum));
    this.threadAnimationStateStore      = new Store<>(new ThreadAnimationState(-1L, null, false));
    this.notificationProfilesRepository = new NotificationProfilesRepository();
    this.searchQuery                    = new MutableLiveData<>();

    LiveData<Recipient>          recipientLiveData  = LiveDataUtil.mapAsync(recipientId, Recipient::resolved);
    LiveData<ThreadAndRecipient> threadAndRecipient = LiveDataUtil.combineLatest(threadId, recipientLiveData, ThreadAndRecipient::new);

    LiveData<ConversationData> metadata = Transformations.switchMap(threadAndRecipient, d -> {
      LiveData<ConversationData> conversationData = conversationRepository.getConversationData(d.threadId, d.recipient, jumpToPosition);

      jumpToPosition = -1;

      return conversationData;
    });

    ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageUpdateObserver);

    LiveData<Pair<Long, PagedData<MessageId, ConversationMessage>>> pagedDataForThreadId = Transformations.map(metadata, data -> {
      int                                 startPosition;
      ConversationData.MessageRequestData messageRequestData = data.getMessageRequestData();

      if (data.shouldJumpToMessage()) {
        startPosition = data.getJumpToPosition();
      } else if (messageRequestData.isMessageRequestAccepted() && data.shouldScrollToLastSeen()) {
        startPosition = data.getLastSeenPosition();
      } else if (messageRequestData.isMessageRequestAccepted()) {
        startPosition = data.getLastScrolledPosition();
      } else {
        startPosition = data.getThreadSize();
      }

      ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver);
      ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver);
      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(data.getThreadId(), conversationObserver);
      ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(data.getThreadId(), messageInsertObserver);

      ConversationDataSource dataSource = new ConversationDataSource(context, data.getThreadId(), messageRequestData, data.showUniversalExpireTimerMessage());
      PagingConfig config = new PagingConfig.Builder().setPageSize(25)
                                                      .setBufferPages(3)
                                                      .setStartIndex(Math.max(startPosition, 0))
                                                      .build();

      Log.d(TAG, "Starting at position: " + startPosition + " || jumpToPosition: " + data.getJumpToPosition() + ", lastSeenPosition: " + data.getLastSeenPosition() + ", lastScrolledPosition: " + data.getLastScrolledPosition());
      return new Pair<>(data.getThreadId(), PagedData.create(dataSource, config));
    });

    this.messages = Transformations.switchMap(pagedDataForThreadId, pair -> {
      pagingController.set(pair.second().getController());
      return pair.second().getData();
    });

    conversationMetadata = Transformations.switchMap(messages, m -> metadata);
    canShowAsBubble      = LiveDataUtil.mapAsync(threadId, conversationRepository::canShowAsBubble);
    wallpaper            = LiveDataUtil.mapDistinct(Transformations.switchMap(recipientId,
                                                                              id -> Recipient.live(id).getLiveData()),
                                                    Recipient::getWallpaper);

    EventBus.getDefault().register(this);

    chatColors = LiveDataUtil.mapDistinct(Transformations.switchMap(recipientId,
                                                                    id -> Recipient.live(id).getLiveData()),
                                          Recipient::getChatColors);

    threadAnimationStateStore.update(threadId, (id, state) -> {
      if (state.getThreadId() == id) {
        return state;
      } else {
        return new ThreadAnimationState(id, null, false);
      }
    });

    threadAnimationStateStore.update(metadata, (m, state) -> {
      if (state.getThreadId() == m.getThreadId()) {
        return state.copy(state.getThreadId(), m, state.getHasCommittedNonEmptyMessageList());
      } else {
        return state.copy(m.getThreadId(), m, false);
      }
    });

    this.threadAnimationStateStoreDriver = state -> {};
    threadAnimationStateStore.getStateLiveData().observeForever(threadAnimationStateStoreDriver);
  }

  void onMessagesCommitted(@NonNull List<ConversationMessage> conversationMessages) {
    if (Util.hasItems(conversationMessages)) {
      threadAnimationStateStore.update(state -> {
        long threadId = conversationMessages.stream()
                                            .filter(Objects::nonNull)
                                            .findFirst()
                                            .map(c -> c.getMessageRecord().getThreadId())
                                            .orElse(-2L);

        if (state.getThreadId() == threadId) {
          return state.copy(state.getThreadId(), state.getThreadMetadata(), true);
        } else {
          return state;
        }
      });
    }
  }

  boolean shouldPlayMessageAnimations() {
    return threadAnimationStateStore.getState().shouldPlayMessageAnimations();
  }

  void setToolbarBottom(int bottom) {
    toolbarBottom.setValue(bottom);
  }

  void setInlinePlayerVisible(boolean isVisible) {
    inlinePlayerHeight.setValue(isVisible ? ViewUtil.dpToPx(36) : 0);
  }

  void onAttachmentKeyboardOpen() {
    mediaRepository.getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, recentMedia::postValue);
  }

  @MainThread
  void onConversationDataAvailable(@NonNull RecipientId recipientId, long threadId, int startingPosition) {
    Log.d(TAG, "[onConversationDataAvailable] recipientId: " + recipientId + ", threadId: " + threadId + ", startingPosition: " + startingPosition);
    this.jumpToPosition = startingPosition;

    this.threadId.setValue(threadId);
    this.recipientId.setValue(recipientId);
  }

  void clearThreadId() {
    this.jumpToPosition = -1;
    this.threadId.postValue(-1L);
  }

  void setSearchQuery(@Nullable String query) {
    searchQuery.setValue(query);
  }

  @NonNull LiveData<String> getSearchQuery() {
    return searchQuery;
  }

  @NonNull LiveData<Integer> getConversationTopMargin() {
    return conversationTopMargin;
  }

  @NonNull LiveData<Boolean> canShowAsBubble() {
    return canShowAsBubble;
  }

  @NonNull LiveData<Boolean> getShowScrollToBottom() {
    return Transformations.distinctUntilChanged(showScrollButtons);
  }

  @NonNull LiveData<Boolean> getShowMentionsButton() {
    return Transformations.distinctUntilChanged(LiveDataUtil.combineLatest(showScrollButtons, hasUnreadMentions, (a, b) -> a && b));
  }

  @NonNull LiveData<ChatWallpaper> getWallpaper() {
    return wallpaper;
  }

  @NonNull LiveData<Event> getEvents() {
    return events;
  }

  @NonNull LiveData<ChatColors> getChatColors() {
    return chatColors;
  }

  void setHasUnreadMentions(boolean hasUnreadMentions) {
    this.hasUnreadMentions.setValue(hasUnreadMentions);
  }

  boolean getShowScrollButtons() {
    return this.showScrollButtons.getValue();
  }

  void setShowScrollButtons(boolean showScrollButtons) {
    this.showScrollButtons.setValue(showScrollButtons);
  }

  @NonNull LiveData<List<Media>> getRecentMedia() {
    return recentMedia;
  }

  @NonNull LiveData<ConversationData> getConversationMetadata() {
    return conversationMetadata;
  }

  @NonNull LiveData<List<ConversationMessage>> getMessages() {
    return messages;
  }

  @NonNull PagingController getPagingController() {
    return pagingController;
  }

  @NonNull LiveData<Map<RecipientId, NameColor>> getNameColorsMap() {
    LiveData<Recipient>         recipient    = Transformations.switchMap(recipientId, r -> Recipient.live(r).getLiveData());
    LiveData<Optional<GroupId>> group        = Transformations.map(recipient, Recipient::getGroupId);
    LiveData<Set<Recipient>>    groupMembers = Transformations.switchMap(group, g -> {
      //noinspection CodeBlock2Expr
      return g.transform(this::getSessionGroupRecipients)
              .or(() -> new DefaultValueLiveData<>(Collections.emptySet()));
    });

    return Transformations.map(groupMembers, members -> {
      List<Recipient> sorted = Stream.of(members)
                                     .filter(member -> !Objects.equals(member, Recipient.self()))
                                     .sortBy(Recipient::requireStringId)
                                     .toList();

      List<NameColor>             names  = ChatColorsPalette.Names.getAll();
      Map<RecipientId, NameColor> colors = new HashMap<>();
      for (int i = 0; i < sorted.size(); i++) {
        colors.put(sorted.get(i).getId(), names.get(i % names.size()));
      }

      return colors;
    });
  }

  private @NonNull LiveData<Set<Recipient>> getSessionGroupRecipients(@NonNull GroupId groupId) {
    LiveData<List<Recipient>> fullMembers = Transformations.map(new LiveGroup(groupId).getFullMembers(),
                                                                members -> Stream.of(members)
                                                                                 .map(GroupMemberEntry.FullMember::getMember)
                                                                                 .toList());

    return Transformations.map(fullMembers, currentMembership -> {
      Set<Recipient> cachedMembers = MapUtil.getOrDefault(sessionMemberCache, groupId, new HashSet<>());
      cachedMembers.addAll(currentMembership);
      sessionMemberCache.put(groupId, cachedMembers);
      return cachedMembers;
    });
  }

  @NonNull LiveData<Optional<NotificationProfile>> getActiveNotificationProfile() {
    final Observable<Optional<NotificationProfile>> activeProfile = Observable.combineLatest(Observable.interval(0, 30, TimeUnit.SECONDS), notificationProfilesRepository.getProfiles(), (interval, profiles) -> profiles)
                                                                              .map(profiles -> Optional.fromNullable(NotificationProfiles.getActiveProfile(profiles)));

    return LiveDataReactiveStreams.fromPublisher(activeProfile.toFlowable(BackpressureStrategy.LATEST));
  }

  long getLastSeen() {
    return conversationMetadata.getValue() != null ? conversationMetadata.getValue().getLastSeen() : 0;
  }

  int getLastSeenPosition() {
    return conversationMetadata.getValue() != null ? conversationMetadata.getValue().getLastSeenPosition() : 0;
  }

  void setArgs(@NonNull ConversationIntents.Args args) {
    this.args = args;
  }

  @NonNull ConversationIntents.Args getArgs() {
    return Objects.requireNonNull(args);
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void onRecaptchaRequiredEvent(@NonNull RecaptchaRequiredEvent event) {
    events.postValue(Event.SHOW_RECAPTCHA);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    threadAnimationStateStore.getStateLiveData().removeObserver(threadAnimationStateStoreDriver);
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver);
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageUpdateObserver);
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver);
    EventBus.getDefault().unregister(this);
  }

  enum Event {
    SHOW_RECAPTCHA
  }

  private static class ThreadAndRecipient {

    private final long      threadId;
    private final Recipient recipient;

    public ThreadAndRecipient(long threadId, Recipient recipient) {
      this.threadId  = threadId;
      this.recipient = recipient;
    }
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationViewModel());
    }
  }
}
