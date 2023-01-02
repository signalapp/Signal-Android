package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

class ConversationRepository {

  private static final String TAG = Log.tag(ConversationRepository.class);

  private final Context  context;

  ConversationRepository() {
    this.context = ApplicationDependencies.getApplication();
  }

  @WorkerThread
  boolean canShowAsBubble(long threadId) {
    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      Recipient recipient = SignalDatabase.threads().getRecipientForThreadId(threadId);

      return recipient != null && BubbleUtil.canBubble(context, recipient.getId(), threadId);
    } else {
      return false;
    }
  }

  @WorkerThread
  public @NonNull ConversationData getConversationData(long threadId, @NonNull Recipient conversationRecipient, int jumpToPosition) {
    ThreadTable.ConversationMetadata metadata   = SignalDatabase.threads().getConversationMetadata(threadId);
    int                              threadSize = SignalDatabase.messages().getMessageCountForThread(threadId);
    long                                lastSeen                       = metadata.getLastSeen();
    int                                 lastSeenPosition               = 0;
    long                                lastScrolled                   = metadata.getLastScrolled();
    int                                 lastScrolledPosition           = 0;
    boolean                             isMessageRequestAccepted       = RecipientUtil.isMessageRequestAccepted(context, threadId);
    ConversationData.MessageRequestData messageRequestData             = new ConversationData.MessageRequestData(isMessageRequestAccepted);
    boolean                             showUniversalExpireTimerUpdate = false;

    if (lastSeen > 0) {
      lastSeenPosition = SignalDatabase.messages().getMessagePositionOnOrAfterTimestamp(threadId, lastSeen);
    }

    if (lastSeenPosition <= 0) {
      lastSeen = 0;
    }

    if (lastSeen == 0 && lastScrolled > 0) {
      lastScrolledPosition = SignalDatabase.messages().getMessagePositionOnOrAfterTimestamp(threadId, lastScrolled);
    }

    if (!isMessageRequestAccepted) {
      boolean isGroup                             = false;
      boolean recipientIsKnownOrHasGroupsInCommon = false;
      if (conversationRecipient.isGroup()) {
        Optional<GroupRecord> group = SignalDatabase.groups().getGroup(conversationRecipient.getId());
        if (group.isPresent()) {
          List<Recipient> recipients = Recipient.resolvedList(group.get().getMembers());
          for (Recipient recipient : recipients) {
            if ((recipient.isProfileSharing() || recipient.hasGroupsInCommon()) && !recipient.isSelf()) {
              recipientIsKnownOrHasGroupsInCommon = true;
              break;
            }
          }
        }
        isGroup = true;
      } else if (conversationRecipient.hasGroupsInCommon()) {
        recipientIsKnownOrHasGroupsInCommon = true;
      }
      messageRequestData = new ConversationData.MessageRequestData(isMessageRequestAccepted, recipientIsKnownOrHasGroupsInCommon, isGroup);
    }

    if (SignalStore.settings().getUniversalExpireTimer() != 0 &&
        conversationRecipient.getExpiresInSeconds() == 0 &&
        !conversationRecipient.isGroup() &&
        conversationRecipient.isRegistered() &&
        (threadId == -1 || !SignalDatabase.messages().hasMeaningfulMessage(threadId)))
    {
      showUniversalExpireTimerUpdate = true;
    }

    return new ConversationData(threadId, lastSeen, lastSeenPosition, lastScrolledPosition, jumpToPosition, threadSize, messageRequestData, showUniversalExpireTimerUpdate);
  }

  void markGiftBadgeRevealed(long messageId) {
    SignalExecutors.BOUNDED_IO.execute(() -> {
      List<MessageTable.MarkedMessageInfo> markedMessageInfo = SignalDatabase.messages().setOutgoingGiftsRevealed(Collections.singletonList(messageId));
      if (!markedMessageInfo.isEmpty()) {
        Log.d(TAG, "Marked gift badge revealed. Sending view sync message.");
        MultiDeviceViewedUpdateJob.enqueue(
            markedMessageInfo.stream()
                             .map(MessageTable.MarkedMessageInfo::getSyncMessageId)
                             .collect(Collectors.toList()));
      }
    });
  }

  @NonNull Single<Boolean> checkIfMmsIsEnabled() {
    return Single.fromCallable(() -> Util.isMmsCapable(context)).subscribeOn(Schedulers.io());
  }

  /**
   * Watches the given recipient id for changes, and gets the security info for the recipient
   * whenever a change occurs.
   *
   * @param recipientId The recipient id we are interested in
   *
   * @return The recipient's security info.
   */
  @NonNull Observable<ConversationSecurityInfo> getSecurityInfo(@NonNull RecipientId recipientId) {
    return Recipient.observable(recipientId)
                    .distinctUntilChanged((lhs, rhs) -> lhs.isPushGroup() == rhs.isPushGroup() && lhs.getRegistered().equals(rhs.getRegistered()))
                    .switchMapSingle(this::getSecurityInfo)
                    .subscribeOn(Schedulers.io());
  }

  private @NonNull Single<ConversationSecurityInfo> getSecurityInfo(@NonNull Recipient recipient) {
    return Single.fromCallable(() -> {
      Log.i(TAG, "Resolving registered state...");
      RecipientTable.RegisteredState registeredState;

      if (recipient.isPushGroup()) {
        Log.i(TAG, "Push group recipient...");
        registeredState = RecipientTable.RegisteredState.REGISTERED;
      } else {
        Log.i(TAG, "Checking through resolved recipient");
        registeredState = recipient.getRegistered();
      }

      Log.i(TAG, "Resolved registered state: " + registeredState);
      boolean signalEnabled = Recipient.self().isRegistered();

      if (registeredState == RecipientTable.RegisteredState.UNKNOWN) {
        try {
          Log.i(TAG, "Refreshing directory for user: " + recipient.getId().serialize());
          registeredState = ContactDiscovery.refresh(context, recipient, false);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      long threadId = SignalDatabase.threads().getThreadIdIfExistsFor(recipient.getId());

      boolean hasUnexportedInsecureMessages = threadId != -1 && SignalDatabase.messages().getUnexportedInsecureMessagesCount(threadId) > 0;

      Log.i(TAG, "Returning registered state...");
      return new ConversationSecurityInfo(recipient.getId(),
                                          registeredState == RecipientTable.RegisteredState.REGISTERED && signalEnabled,
                                          Util.isDefaultSmsProvider(context),
                                          true,
                                          hasUnexportedInsecureMessages);
    }).subscribeOn(Schedulers.io());
  }

  Observable<Integer> getUnreadCount(long threadId, long afterTime) {
    if (threadId <= -1L || afterTime <= 0L) {
      return Observable.just(0);
    }

    return Observable.<Integer> create(emitter -> {

      DatabaseObserver.Observer listener = () -> emitter.onNext(SignalDatabase.messages().getIncomingMeaningfulMessageCountSince(threadId, afterTime));

      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(threadId, listener);
      emitter.setCancellable(() -> ApplicationDependencies.getDatabaseObserver().unregisterObserver(listener));

      listener.onChanged();
    }).subscribeOn(Schedulers.io());
  }

  public void insertSmsExportUpdateEvent(Recipient recipient) {
    SignalExecutors.BOUNDED.execute(() -> {
      long threadId = SignalDatabase.threads().getThreadIdIfExistsFor(recipient.getId());

      if (threadId == -1 || !Util.isDefaultSmsProvider(context)) {
        return;
      }

      if (RecipientUtil.isSmsOnly(threadId, recipient) && (!recipient.isMmsGroup() || Util.isDefaultSmsProvider(context))) {
        SignalDatabase.messages().insertSmsExportMessage(recipient.getId(), threadId);
      }
    });
  }
}
