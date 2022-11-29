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
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MmsSmsTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

  ConversationDataSource(@NonNull Context context, long threadId, @NonNull MessageRequestData messageRequestData, boolean showUniversalExpireTimerUpdate, int baseSize) {
    this.context                        = context;
    this.threadId                       = threadId;
    this.messageRequestData             = messageRequestData;
    this.showUniversalExpireTimerUpdate = showUniversalExpireTimerUpdate;
    this.baseSize                       = baseSize;
  }

  @Override
  public int size() {
    long startTime = System.currentTimeMillis();
    int  size      = getSizeInternal() +
                     (messageRequestData.includeWarningUpdateMessage() ? 1 : 0) +
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

    return SignalDatabase.mmsSms().getConversationCount(threadId);
  }

  @Override
  public @NonNull List<ConversationMessage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    Stopwatch           stopwatch = new Stopwatch("load(" + start + ", " + length + "), thread " + threadId);
    MmsSmsTable         db        = SignalDatabase.mmsSms();
    List<MessageRecord> records   = new ArrayList<>(length);
    MentionHelper       mentionHelper    = new MentionHelper();
    AttachmentHelper    attachmentHelper = new AttachmentHelper();
    ReactionHelper      reactionHelper   = new ReactionHelper();
    PaymentHelper       paymentHelper    = new PaymentHelper();
    Set<ServiceId>      referencedIds    = new HashSet<>();

    try (MmsSmsTable.Reader reader = MmsSmsTable.readerFor(db.getConversation(threadId, start, length))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null && !cancellationSignal.isCanceled()) {
        records.add(record);
        mentionHelper.add(record);
        reactionHelper.add(record);
        attachmentHelper.add(record);
        paymentHelper.add(record);

        UpdateDescription description = record.getUpdateDisplayBody(context, null);
        if (description != null) {
          referencedIds.addAll(description.getMentioned());
        }
      }
    }

    if (messageRequestData.includeWarningUpdateMessage() && (start + length >= size())) {
      records.add(new InMemoryMessageRecord.NoGroupsInCommon(threadId, messageRequestData.isGroup()));
    }

    if (showUniversalExpireTimerUpdate) {
      records.add(new InMemoryMessageRecord.UniversalExpireTimerUpdate(threadId));
    }

    stopwatch.split("messages");

    mentionHelper.fetchMentions(context);
    stopwatch.split("mentions");

    reactionHelper.fetchReactions();
    stopwatch.split("reactions");

    records = reactionHelper.buildUpdatedModels(records);
    stopwatch.split("reaction-models");

    attachmentHelper.fetchAttachments(context);
    stopwatch.split("attachments");

    records = attachmentHelper.buildUpdatedModels(context, records);
    stopwatch.split("attachment-models");

    paymentHelper.fetchPayments();
    stopwatch.split("payments");

    records = paymentHelper.buildUpdatedModels(records);
    stopwatch.split("payment-models");

    for (ServiceId serviceId : referencedIds) {
      Recipient.resolved(RecipientId.from(serviceId));
    }
    stopwatch.split("recipient-resolves");

    List<ConversationMessage> messages = Stream.of(records)
                                               .map(m -> ConversationMessageFactory.createWithUnresolvedData(context, m, mentionHelper.getMentions(m.getId())))
                                               .toList();

    stopwatch.split("conversion");
    stopwatch.stop(TAG);

    return messages;
  }

  @Override
  public @Nullable ConversationMessage load(@NonNull MessageId messageId) {
    Stopwatch     stopwatch = new Stopwatch("load(" + messageId + "), thread " + threadId);
    MessageTable  database  = messageId.isMms() ? SignalDatabase.mms() : SignalDatabase.sms();
    MessageRecord record    = database.getMessageRecordOrNull(messageId.getId());

    if (record instanceof MediaMmsMessageRecord &&
        ((MediaMmsMessageRecord) record).getParentStoryId() != null &&
        ((MediaMmsMessageRecord) record).getParentStoryId().isGroupReply()) {
      return null;
    }

    stopwatch.split("message");

    try {
      if (record != null) {
        List<Mention> mentions;
        if (messageId.isMms()) {
          mentions = SignalDatabase.mentions().getMentionsForMessage(messageId.getId());
        } else {
          mentions = Collections.emptyList();
        }

        stopwatch.split("mentions");

        List<ReactionRecord> reactions = SignalDatabase.reactions().getReactions(messageId);
        record = ReactionHelper.recordWithReactions(record, reactions);

        stopwatch.split("reactions");

        if (messageId.isMms()) {
          List<DatabaseAttachment> attachments = SignalDatabase.attachments().getAttachmentsForMessage(messageId.getId());
          if (attachments.size() > 0) {
            record = ((MediaMmsMessageRecord) record).withAttachments(context, attachments);
          }
        }

        stopwatch.split("attachments");

        if (record.isPaymentNotification()) {
          record = SignalDatabase.payments().updateMessageWithPayment(record);
        }

        stopwatch.split("payments");

        return ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(ApplicationDependencies.getApplication(), record, mentions);
      } else {
        return null;
      }
    } finally {
      stopwatch.stop(TAG);
    }
  }

  @Override
  public @NonNull MessageId getKey(@NonNull ConversationMessage conversationMessage) {
    return new MessageId(conversationMessage.getMessageRecord().getId(), conversationMessage.getMessageRecord().isMms());
  }

  private static class MentionHelper {

    private Collection<Long>         messageIds          = new LinkedList<>();
    private Map<Long, List<Mention>> messageIdToMentions = new HashMap<>();

    void add(MessageRecord record) {
      if (record.isMms()) {
        messageIds.add(record.getId());
      }
    }

    void fetchMentions(Context context) {
      messageIdToMentions = SignalDatabase.mentions().getMentionsForMessages(messageIds);
    }

    @Nullable List<Mention> getMentions(long id) {
      return messageIdToMentions.get(id);
    }
  }

  private static class AttachmentHelper {

    private Collection<Long>                    messageIds             = new LinkedList<>();
    private Map<Long, List<DatabaseAttachment>> messageIdToAttachments = new HashMap<>();

    void add(MessageRecord record) {
      if (record.isMms()) {
        messageIds.add(record.getId());
      }
    }

    void fetchAttachments(Context context) {
      messageIdToAttachments = SignalDatabase.attachments().getAttachmentsForMessages(messageIds);
    }

    @NonNull List<MessageRecord> buildUpdatedModels(@NonNull Context context, @NonNull List<MessageRecord> records) {
      return records.stream()
                    .map(record -> {
                      if (record instanceof MediaMmsMessageRecord) {
                        List<DatabaseAttachment> attachments = messageIdToAttachments.get(record.getId());

                        if (Util.hasItems(attachments)) {
                          return ((MediaMmsMessageRecord) record).withAttachments(context, attachments);
                        }
                      }

                      return record;
                    })
                    .collect(Collectors.toList());
    }
  }

  public static class ReactionHelper {

    private Collection<MessageId>                messageIds           = new LinkedList<>();
    private Map<MessageId, List<ReactionRecord>> messageIdToReactions = new HashMap<>();

    public void add(MessageRecord record) {
      messageIds.add(new MessageId(record.getId(), record.isMms()));
    }

    public void addAll(List<MessageRecord> records) {
      for (MessageRecord record : records) {
        add(record);
      }
    }

    public void fetchReactions() {
      messageIdToReactions = SignalDatabase.reactions().getReactionsForMessages(messageIds);
    }

    public @NonNull List<MessageRecord> buildUpdatedModels(@NonNull List<MessageRecord> records) {
      return records.stream()
                    .map(record -> {
                      MessageId            messageId = new MessageId(record.getId(), record.isMms());
                      List<ReactionRecord> reactions = messageIdToReactions.get(messageId);

                      return recordWithReactions(record, reactions);
                    })
                    .collect(Collectors.toList());
    }

    private static MessageRecord recordWithReactions(@NonNull MessageRecord record, List<ReactionRecord> reactions) {
      if (Util.hasItems(reactions)) {
        if (record instanceof MediaMmsMessageRecord) {
          return ((MediaMmsMessageRecord) record).withReactions(reactions);
        } else if (record instanceof SmsMessageRecord) {
          return ((SmsMessageRecord) record).withReactions(reactions);
        } else {
          throw new IllegalStateException("We have reactions for an unsupported record type: " + record.getClass().getName());
        }
      } else {
        return record;
      }
    }
  }

  private static class PaymentHelper {
    private final Map<UUID, Long>    paymentMessages    = new HashMap<>();
    private final Map<Long, Payment> messageIdToPayment = new HashMap<>();

    public void add(MessageRecord messageRecord) {
      if (messageRecord.isMms() && messageRecord.isPaymentNotification()) {
        UUID paymentUuid = UuidUtil.parseOrNull(messageRecord.getBody());
        if (paymentUuid != null) {
          paymentMessages.put(paymentUuid, messageRecord.getId());
        }
      }
    }

    public void fetchPayments() {
      List<Payment> payments = SignalDatabase.payments().getPayments(paymentMessages.keySet());
      for (Payment payment : payments) {
        if (payment != null) {
          messageIdToPayment.put(paymentMessages.get(payment.getUuid()), payment);
        }
      }
    }

    @NonNull List<MessageRecord> buildUpdatedModels(@NonNull List<MessageRecord> records) {
      return records.stream()
                    .map(record -> {
                      if (record instanceof MediaMmsMessageRecord) {
                        Payment payment = messageIdToPayment.get(record.getId());
                        if (payment != null) {
                          return ((MediaMmsMessageRecord) record).withPayment(payment);
                        }
                      }
                      return record;
                    })
                    .collect(Collectors.toList());
    }
  }
}
