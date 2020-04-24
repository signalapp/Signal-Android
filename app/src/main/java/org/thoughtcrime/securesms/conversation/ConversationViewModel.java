package org.thoughtcrime.securesms.conversation;

import android.app.Application;
import android.content.Context;
import android.database.ContentObservable;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaRepository;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

class ConversationViewModel extends ViewModel {

  private static final String TAG = Log.tag(ConversationViewModel.class);

  private static final int NO_LIMIT = 0;

  private final Application                       context;
  private final MediaRepository                   mediaRepository;
  private final ConversationRepository            conversationRepository;
  private final MutableLiveData<List<Media>>      recentMedia;
  private final MutableLiveData<ConversationData> conversation;
  private final ContentObserver                   contentObserver;

  private Recipient recipient;
  private long      threadId;
  private boolean   firstLoad;
  private int       requestedLimit;
  private long      lastSeen;
  private int       startingPosition;
  private int       previousOffset;
  private boolean   contentObserverRegistered;

  private ConversationViewModel() {
    this.context                = ApplicationDependencies.getApplication();
    this.mediaRepository        = new MediaRepository();
    this.conversationRepository = new ConversationRepository();
    this.recentMedia            = new MutableLiveData<>();
    this.conversation           = new MutableLiveData<>();
    this.contentObserver        = new ContentObserver(new Handler()) {
      @Override
      public void onChange(boolean selfChange) {
        ConversationData data = conversation.getValue();
        if (data != null) {
          conversationRepository.getConversationData(threadId, data.getOffset(), data.getLimit(), data.getLastSeen(), data.getPreviousOffset(), data.isFirstLoad(), conversation::postValue);
        } else {
          Log.w(TAG, "Got a content change, but have no previous data?");
        }
      }
    };
  }

  void onAttachmentKeyboardOpen() {
    mediaRepository.getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, recentMedia::postValue);
  }

  void onConversationDataAvailable(Recipient recipient, long threadId, long lastSeen, int startingPosition, int limit) {
    this.recipient        = recipient;
    this.threadId         = threadId;
    this.lastSeen         = lastSeen;
    this.startingPosition = startingPosition;
    this.requestedLimit   = limit;
    this.firstLoad        = true;

    if (!contentObserverRegistered) {
      context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadId), true, contentObserver);
      contentObserverRegistered = true;
    }

    refreshConversation();
  }

  void refreshConversation() {
    int limit  = requestedLimit;
    int offset = 0;

    if (requestedLimit != NO_LIMIT && startingPosition >= requestedLimit) {
      offset = Math.max(startingPosition - (requestedLimit / 2) + 1, 0);
      startingPosition -= offset - 1;
    }

    conversationRepository.getConversationData(threadId, offset, limit, lastSeen, previousOffset, firstLoad, conversation::postValue);

    if (firstLoad) {
      firstLoad = false;
    }

    previousOffset = offset;
  }

  void onLoadMoreClicked() {
    requestedLimit = 0;
    refreshConversation();
  }

  void onMoveJumpToMessageOutOfRange(int startingPosition) {
    this.firstLoad        = true;
    this.startingPosition = startingPosition;

    refreshConversation();
  }

  void onLastSeenChanged(long lastSeen) {
    this.lastSeen = lastSeen;
  }

  @NonNull LiveData<List<Media>> getRecentMedia() {
    return recentMedia;
  }

  @NonNull LiveData<ConversationData> getConversation() {
    return conversation;
  }

  long getLastSeen() {
    return lastSeen;
  }

  int getStartingPosition() {
    return startingPosition;
  }

  int getActiveOffset() {
    ConversationData data = conversation.getValue();
    return data != null ? data.getOffset() : 0;
  }

  @Override
  protected void onCleared() {
    context.getContentResolver().unregisterContentObserver(contentObserver);
    contentObserverRegistered = false;
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ConversationViewModel());
    }
  }
}
