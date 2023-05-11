package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.signal.paging.PagedDataSource;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.conversation.ConversationData.MessageRequestData;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.conversation.v2.data.AttachmentHelper;
import org.thoughtcrime.securesms.conversation.v2.data.CallHelper;
import org.thoughtcrime.securesms.conversation.v2.data.MentionHelper;
import org.thoughtcrime.securesms.conversation.v2.data.PaymentHelper;
import org.thoughtcrime.securesms.conversation.v2.data.QuotedHelper;
import org.thoughtcrime.securesms.conversation.v2.data.ReactionHelper;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core data source for loading an individual conversation.
 */
public class ConversationDataSource implements PagedDataSource<MessageId, ConversationMessage> {

  private static final String TAG = Log.tag(ConversationDataSource.class);

  private final Context            context;
  private final long               threadId;
  private final MessageRequestData messageRequestData;
  private final boolean            showUniversalExpireTimerUpdate;

  /** Used once for the initial fetch, then cleared. */
  private int baseSize;

  private final Recipient threadRecipient;

  public ConversationDataSource(
      @NonNull Context context,
      long threadId,
      @NonNull MessageRequestData messageRequestData,
      boolean showUniversalExpireTimerUpdate,
      int baseSize,
      @NonNull Recipient threadRecipient
  ) {
    this.context                        = context;
    this.threadId                       = threadId;
    this.messageRequestData             = messageRequestData;
    this.showUniversalExpireTimerUpdate = showUniversalExpireTimerUpdate;
    this.baseSize                       = baseSize;
    this.threadRecipient                = threadRecipient;
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int  size      = getSizeInternal() +
                     (messageRequestData.includeWarningUpdateMessage() ? 1 : 0) +
                     (messageRequestData.isHidden() ? 1 : 0) +
                     (showUniversalExpireTimerUpdate ? 1 : 0);

    Log.d(TAG, "[size(), thread " + threadId + "] " + (System.currentTimeMillis() - startTime) + " ms");

    return size;
  }

  private int getSizeInternal() {
    synchronized (this) {
      if (baseSize != -1) {
        int size = baseSize;
        baseSize = -1;
        return size;
      }
    }

    return SignalDatabase.messages().getMessageCountForThread(threadId);
  }

  @Override
  public @NonNull List<ConversationMessage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    Stopwatch           stopwatch        = new Stopwatch("load(" + start + ", " + length + "), thread " + threadId);
    List<MessageRecord> records          = new ArrayList<>(length);
    MentionHelper       mentionHelper    = new MentionHelper();
    QuotedHelper        quotedHelper     = new QuotedHelper();
    AttachmentHelper    attachmentHelper = new AttachmentHelper();
    ReactionHelper      reactionHelper   = new ReactionHelper();
    PaymentHelper       paymentHelper    = new PaymentHelper();
    CallHelper          callHelper       = new CallHelper();
    Set<ServiceId>      referencedIds    = new HashSet<>();

    try (MessageTable.Reader reader = MessageTable.mmsReaderFor(SignalDatabase.messages().getConversation(threadId, start, length))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        records.add(record);
        mentionHelper.add(record);
        quotedHelper.add(record);
        reactionHelper.add(record);
        attachmentHelper.add(record);
        paymentHelper.add(record);
        callHelper.add(record);

        UpdateDescription description = record.getUpdateDisplayBody(context, null);
        if (description != null) {
          referencedIds.addAll(description.getMentioned());
        }
      }
    }

    if (messageRequestData.includeWarningUpdateMessage() && (start + length >= size())) {
      records.add(new InMemoryMessageRecord.NoGroupsInCommon(threadId, messageRequestData.isGroup()));
    }

    if (messageRequestData.isHidden() && (start + length >= size())) {
      records.add(new InMemoryMessageRecord.RemovedContactHidden(threadId));
    }

    if (showUniversalExpireTimerUpdate) {
      records.add(new InMemoryMessageRecord.UniversalExpireTimerUpdate(threadId));
    }

    stopwatch.split("messages");

    mentionHelper.fetchMentions(context);
    stopwatch.split("mentions");

    quotedHelper.fetchQuotedState();
    stopwatch.split("is-quoted");

    reactionHelper.fetchReactions();
    stopwatch.split("reactions");

    records = reactionHelper.buildUpdatedModels(records);
    stopwatch.split("reaction-models");

    attachmentHelper.fetchAttachments();
    stopwatch.split("attachments");

    records = attachmentHelper.buildUpdatedModels(context, records);
    stopwatch.split("attachment-models");

    paymentHelper.fetchPayments();
    stopwatch.split("payments");

    records = paymentHelper.buildUpdatedModels(records);
    stopwatch.split("payment-models");

    callHelper.fetchCalls();
    stopwatch.split("calls");

    records = callHelper.buildUpdatedModels(records);
    stopwatch.split("call-models");

    for (ServiceId serviceId : referencedIds) {
      Recipient.resolved(RecipientId.from(serviceId));
    }
    stopwatch.split("recipient-resolves");

    List<ConversationMessage> messages = Stream.of(records)
                                               .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, m.getDisplayBody(context), mentionHelper.getMentions(m.getId()), quotedHelper.isQuoted(m.getId()), threadRecipient))
                                               .toList();

    stopwatch.split("conversion");
    stopwatch.stop(TAG);

    return messages;
  }

  @Override
  public @Nullable ConversationMessage load(@NonNull MessageId messageId) {
    Stopwatch     stopwatch = new Stopwatch("load(" + messageId + "), thread " + threadId);
    MessageRecord record    = SignalDatabase.messages().getMessageRecordOrNull(messageId.getId());

    if (record instanceof MediaMmsMessageRecord &&
        ((MediaMmsMessageRecord) record).getParentStoryId() != null &&
        ((MediaMmsMessageRecord) record).getParentStoryId().isGroupReply()) {
      return null;
    }

    if (record instanceof MediaMmsMessageRecord && ((MediaMmsMessageRecord) record).getScheduledDate() != -1) {
      return null;
    }

    stopwatch.split("message");

    try {
      if (record != null) {
        List<Mention> mentions = SignalDatabase.mentions().getMentionsForMessage(messageId.getId());
        stopwatch.split("mentions");

        boolean isQuoted = SignalDatabase.messages().isQuoted(record);
        stopwatch.split("is-quoted");

        List<ReactionRecord> reactions = SignalDatabase.reactions().getReactions(messageId);
        record = ReactionHelper.recordWithReactions(record, reactions);
        stopwatch.split("reactions");

        List<DatabaseAttachment> attachments = SignalDatabase.attachments().getAttachmentsForMessage(messageId.getId());
        if (attachments.size() > 0) {
          record = ((MediaMmsMessageRecord) record).withAttachments(context, attachments);
        }
        stopwatch.split("attachments");

        if (record.isPaymentNotification()) {
          record = SignalDatabase.payments().updateMessageWithPayment(record);
        }
        stopwatch.split("payments");

        if (record.isCallLog() && !record.isGroupCall()) {
          CallTable.Call call = SignalDatabase.calls().getCallByMessageId(record.getId());
          if (call != null && record instanceof MediaMmsMessageRecord) {
            record = ((MediaMmsMessageRecord) record).withCall(call);
          }
        }

        stopwatch.split("calls");

        return ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(ApplicationDependencies.getApplication(),
                                                                                       record,
                                                                                       record.getDisplayBody(ApplicationDependencies.getApplication()),
                                                                                       mentions,
                                                                                       isQuoted,
                                                                                       threadRecipient);
      } else {
        return null;
      }
    } finally {
      stopwatch.stop(TAG);
    }
  }

  @Override
  public @NonNull MessageId getKey(@NonNull ConversationMessage conversationMessage) {
    return new MessageId(conversationMessage.getMessageRecord().getId());
  }
}
