package org.thoughtcrime.securesms.conversation;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.signal.paging.PagedData;
import org.signal.paging.PagingConfig;
import org.signal.paging.PagingController;
import org.signal.paging.ProxyPagingController;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaRepository;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.libsignal.util.Pair;

import java.util.List;
import java.util.Objects;

class ConversationViewModel extends ViewModel {

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
  private final ProxyPagingController               pagingController;
  private final DatabaseObserver.Observer           messageObserver;
  private final MutableLiveData<RecipientId>        recipientId;
  private final LiveData<ChatWallpaper>             wallpaper;

  private ConversationIntents.Args args;
  private int                      jumpToPosition;

  private ConversationViewModel() {
    this.context                = ApplicationDependencies.getApplication();
    this.mediaRepository        = new MediaRepository();
    this.conversationRepository = new ConversationRepository();
    this.recentMedia            = new MutableLiveData<>();
    this.threadId               = new MutableLiveData<>();
    this.showScrollButtons      = new MutableLiveData<>(false);
    this.hasUnreadMentions      = new MutableLiveData<>(false);
    this.recipientId            = new MutableLiveData<>();
    this.pagingController       = new ProxyPagingController();
    this.messageObserver        = pagingController::onDataInvalidated;

    LiveData<ConversationData> metadata = Transformations.switchMap(threadId, thread -> {
      LiveData<ConversationData> conversationData = conversationRepository.getConversationData(thread, jumpToPosition);

      jumpToPosition = -1;

      return conversationData;
    });

    LiveData<Pair<Long, PagedData<ConversationMessage>>> pagedDataForThreadId = Transformations.map(metadata, data -> {
      final int startPosition;
      if (data.shouldJumpToMessage()) {
        startPosition = data.getJumpToPosition();
      } else if (data.isMessageRequestAccepted() && data.shouldScrollToLastSeen()) {
        startPosition = data.getLastSeenPosition();
      } else if (data.isMessageRequestAccepted()) {
        startPosition = data.getLastScrolledPosition();
      } else {
        startPosition = data.getThreadSize();
      }

      ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageObserver);
      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(data.getThreadId(), messageObserver);

      ConversationDataSource dataSource = new ConversationDataSource(context, data.getThreadId());
      PagingConfig           config     = new PagingConfig.Builder()
                                                          .setPageSize(25)
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
    wallpaper            = Transformations.distinctUntilChanged(Transformations.map(Transformations.switchMap(recipientId,
                                                                                                              id -> Recipient.live(id).getLiveData()),
                                                                                    Recipient::getWallpaper));
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

  void setHasUnreadMentions(boolean hasUnreadMentions) {
    this.hasUnreadMentions.setValue(hasUnreadMentions);
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

  @Override
  protected void onCleared() {
    super.onCleared();
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageObserver);
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationViewModel());
    }
  }
}
