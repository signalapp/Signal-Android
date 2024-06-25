package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.StringUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.ContactRepository;
import org.thoughtcrime.securesms.conversation.MessageStyler;
import org.thoughtcrime.securesms.database.BodyAdjustment;
import org.thoughtcrime.securesms.database.BodyRangeUtil;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MentionTable;
import org.thoughtcrime.securesms.database.MentionUtil;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SearchTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.thoughtcrime.securesms.database.SearchTable.SNIPPET_WRAP;

/**
 * Manages data retrieval for search.
 */
public class SearchRepository {

  private static final String TAG = Log.tag(SearchRepository.class);

  private final Context           context;
  private final String            noteToSelfTitle;
  private final SearchTable       searchDatabase;
  private final ContactRepository contactRepository;
  private final ThreadTable       threadTable;
  private final RecipientTable    recipientTable;
  private final MentionTable      mentionTable;
  private final MessageTable      messageTable;

  private final Executor serialExecutor;

  public SearchRepository(@NonNull String noteToSelfTitle) {
    this.context           = AppDependencies.getApplication().getApplicationContext();
    this.noteToSelfTitle   = noteToSelfTitle;
    this.searchDatabase    = SignalDatabase.messageSearch();
    this.threadTable       = SignalDatabase.threads();
    this.recipientTable    = SignalDatabase.recipients();
    this.mentionTable      = SignalDatabase.mentions();
    this.messageTable      = SignalDatabase.messages();
    this.contactRepository = new ContactRepository(context, noteToSelfTitle);
    this.serialExecutor    = new SerialExecutor(SignalExecutors.BOUNDED);
  }

  @WorkerThread
  public @NonNull ThreadSearchResult queryThreadsSync(@NonNull String query, boolean unreadOnly) {
    long               start  = System.currentTimeMillis();
    List<ThreadRecord> result = queryConversations(query, unreadOnly);

    Log.d(TAG, "[threads] Search took " + (System.currentTimeMillis() - start) + " ms");

    return new ThreadSearchResult(result, query);
  }

  @WorkerThread
  public @NonNull MessageSearchResult queryMessagesSync(@NonNull String query) {
    long start = System.currentTimeMillis();

    List<MessageResult> messages        = queryMessages(query);
    List<MessageResult> mentionMessages = queryMentions(convertMentionsQueryToTokens(query));
    List<MessageResult> combined        = mergeMessagesAndMentions(messages, mentionMessages);

    Log.d(TAG, "[messages] Search took " + (System.currentTimeMillis() - start) + " ms");

    return new MessageSearchResult(combined, query);
  }

  public void query(@NonNull String query, long threadId, @NonNull Callback<List<MessageResult>> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(Collections.emptyList());
      return;
    }

    serialExecutor.execute(() -> {
      long                startTime       = System.currentTimeMillis();
      List<MessageResult> messages        = queryMessages(query, threadId);
      List<MessageResult> mentionMessages = queryMentions(convertMentionsQueryToTokens(query), threadId);

      Log.d(TAG, "[ConversationQuery] " + (System.currentTimeMillis() - startTime) + " ms");

      callback.onResult(mergeMessagesAndMentions(messages, mentionMessages));
    });
  }

  private @NonNull List<ThreadRecord> queryConversations(@NonNull String query, boolean unreadOnly) {
    if (Util.isEmpty(query)) {
      return Collections.emptyList();
    }

    Set<RecipientId> filteredContacts = new LinkedHashSet<>();
    try (Cursor cursor = SignalDatabase.recipients().queryAllContacts(query)) {
      while (cursor != null && cursor.moveToNext()) {
        filteredContacts.add(RecipientId.from(CursorUtil.requireString(cursor, RecipientTable.ID)));
      }
    }

    Set<RecipientId> contactIds = new LinkedHashSet<>(filteredContacts);

    if (noteToSelfTitle.toLowerCase().contains(query.toLowerCase())) {
      contactIds.add(Recipient.self().getId());
    }

    Set<RecipientId> groupsByTitleIds = new LinkedHashSet<>();

    GroupRecord record;
    try (GroupTable.Reader reader = SignalDatabase.groups().queryGroupsByTitle(query, true, false, false)) {
      while ((record = reader.getNext()) != null) {
        groupsByTitleIds.add(record.getRecipientId());
      }
    }

    LinkedHashSet<ThreadRecord> output = new LinkedHashSet<>();

    output.addAll(getMatchingThreads(contactIds, unreadOnly));
    output.addAll(getMatchingThreads(groupsByTitleIds, unreadOnly));

    return new ArrayList<>(output);
  }

  private List<ThreadRecord> getMatchingThreads(@NonNull Collection<RecipientId> recipientIds, boolean unreadOnly) {
    try (Cursor cursor = threadTable.getFilteredConversationList(new ArrayList<>(recipientIds), unreadOnly)) {
      return readToList(cursor, new ThreadModelBuilder(threadTable));
    }
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query) {
    if (Util.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<MessageResult> results;
    try (Cursor cursor = searchDatabase.queryMessages(query)) {
      results = readToList(cursor, new MessageModelBuilder());
    }

    List<Long> messageIds = new LinkedList<>();
    for (MessageResult result : results) {
      if (result.isMms()) {
        messageIds.add(result.getMessageId());
      }
    }

    if (messageIds.isEmpty()) {
      return results;
    }

    Map<Long, BodyRangeList> bodyRanges = SignalDatabase.messages().getBodyRangesForMessages(messageIds);
    Map<Long, List<Mention>> mentions   = SignalDatabase.mentions().getMentionsForMessages(messageIds);

    if (bodyRanges.isEmpty() && mentions.isEmpty()) {
      return results;
    }

    List<MessageResult> updatedResults = new ArrayList<>(results.size());
    for (MessageResult result : results) {
      if (bodyRanges.containsKey(result.getMessageId()) || mentions.containsKey(result.getMessageId())) {
        CharSequence         body               = result.getBody();
        CharSequence         bodySnippet        = result.getBodySnippet();
        CharSequence         updatedBody        = body;
        List<BodyAdjustment> bodyAdjustments    = Collections.emptyList();
        CharSequence         updatedSnippet     = bodySnippet;
        List<BodyAdjustment> snippetAdjustments = Collections.emptyList();
        List<Mention>        messageMentions    = mentions.get(result.getMessageId());
        BodyRangeList        ranges             = bodyRanges.get(result.getMessageId());

        if (messageMentions != null) {
          MentionUtil.UpdatedBodyAndMentions bodyMentionUpdate = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, body, messageMentions);
          updatedBody     = Objects.requireNonNull(bodyMentionUpdate.getBody());
          bodyAdjustments = bodyMentionUpdate.getBodyAdjustments();

          MentionUtil.UpdatedBodyAndMentions snippetMentionUpdate = updateSnippetWithDisplayNames(body, bodySnippet, messageMentions);
          updatedSnippet     = Objects.requireNonNull(snippetMentionUpdate.getBody());
          snippetAdjustments = snippetMentionUpdate.getBodyAdjustments();
        }

        if (ranges != null) {
          updatedBody = SpannableString.valueOf(updatedBody);
          MessageStyler.style(result.getReceivedTimestampMs(), BodyRangeUtil.adjustBodyRanges(ranges, bodyAdjustments), (Spannable) updatedBody);

          updatedSnippet = SpannableString.valueOf(updatedSnippet);
          //noinspection ConstantConditions
          updateSnippetWithStyles(result.getReceivedTimestampMs(), updatedBody, (SpannableString) updatedSnippet, BodyRangeUtil.adjustBodyRanges(ranges, snippetAdjustments));
        }

        updatedResults.add(new MessageResult(result.getConversationRecipient(), result.getMessageRecipient(), updatedBody, updatedSnippet, result.getThreadId(), result.getMessageId(), result.getReceivedTimestampMs(), result.isMms()));
      } else {
        updatedResults.add(result);
      }
    }

    return updatedResults;
  }

  private @NonNull MentionUtil.UpdatedBodyAndMentions updateSnippetWithDisplayNames(@NonNull CharSequence body, @NonNull CharSequence bodySnippet, @NonNull List<Mention> mentions) {
    CharSequence cleanSnippet = bodySnippet;
    int          startOffset  = 0;

    if (StringUtil.startsWith(cleanSnippet, SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.subSequence(SNIPPET_WRAP.length(), cleanSnippet.length());
      startOffset  = SNIPPET_WRAP.length();
    }

    if (StringUtil.endsWith(cleanSnippet, SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.subSequence(0, cleanSnippet.length() - SNIPPET_WRAP.length());
    }

    int startIndex = TextUtils.indexOf(body, cleanSnippet);

    if (startIndex != -1) {
      List<Mention> adjustMentions = new ArrayList<>(mentions.size());
      for (Mention mention : mentions) {
        int adjustedStart = mention.getStart() - startIndex + startOffset;
        if (adjustedStart >= 0 && adjustedStart + mention.getLength() <= cleanSnippet.length()) {
          adjustMentions.add(new Mention(mention.getRecipientId(), adjustedStart, mention.getLength()));
        }
      }

      return MentionUtil.updateBodyAndMentionsWithDisplayNames(context, bodySnippet, adjustMentions);
    } else {
      return MentionUtil.updateBodyAndMentionsWithDisplayNames(context, bodySnippet, Collections.emptyList());
    }
  }

  private void updateSnippetWithStyles(long id, @NonNull CharSequence body, @NonNull SpannableString bodySnippet, @NonNull BodyRangeList bodyRanges) {
    CharSequence cleanSnippet = bodySnippet;
    int          startOffset  = 0;

    if (StringUtil.startsWith(cleanSnippet, SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.subSequence(SNIPPET_WRAP.length(), cleanSnippet.length());
      startOffset  = SNIPPET_WRAP.length();
    }

    if (StringUtil.endsWith(cleanSnippet, SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.subSequence(0, cleanSnippet.length() - SNIPPET_WRAP.length());
    }

    int startIndex = TextUtils.indexOf(body, cleanSnippet);

    if (startIndex != -1) {
      List<BodyRangeList.BodyRange> newRanges = new ArrayList<>(bodyRanges.ranges.size());
      for (BodyRangeList.BodyRange range : bodyRanges.ranges) {
        int adjustedStart = range.start - startIndex + startOffset;
        if (adjustedStart >= 0 && adjustedStart + range.length <= bodySnippet.length()) {
          newRanges.add(range.newBuilder().start(adjustedStart).build());
        }
      }

      BodyRangeList.Builder builder = new BodyRangeList.Builder();
      builder.ranges(newRanges);

      MessageStyler.style(id, builder.build(), bodySnippet);
    }
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query, long threadId) {
    try (Cursor cursor = searchDatabase.queryMessages(query, threadId)) {
      return readToList(cursor, new MessageModelBuilder());
    }
  }

  private @NonNull List<MessageResult> queryMentions(@NonNull List<String> cleanQueries) {
    Set<RecipientId> recipientIds = new HashSet<>();
    for (String cleanQuery : cleanQueries) {
      for (Recipient recipient : recipientTable.queryRecipientsForMentions(cleanQuery)) {
        recipientIds.add(recipient.getId());
      }
    }

    Map<Long, List<Mention>> mentionQueryResults = mentionTable.getMentionsContainingRecipients(recipientIds, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageTable.Reader reader = messageTable.getMessages(mentionQueryResults.keySet())) {
      for (MessageRecord record : reader) {
        BodyRangeList bodyRanges = record.getMessageRanges();
        List<Mention> mentions   = mentionQueryResults.get(record.getId());

        if (Util.hasItems(mentions)) {
          SpannableString body = new SpannableString(record.getBody());

          if (bodyRanges != null) {
            MessageStyler.style(record.getDateSent(), bodyRanges, body);
          }

          CharSequence updatedBody    = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, body, mentions).getBody();
          CharSequence updatedSnippet = makeSnippet(cleanQueries, Objects.requireNonNull(updatedBody));

          results.add(new MessageResult(record.getFromRecipient(), record.getToRecipient(), updatedBody, updatedSnippet, record.getThreadId(), record.getId(), record.getDateReceived(), true));
        }
      }
    }

    return results;
  }

  private @NonNull List<MessageResult> queryMentions(@NonNull List<String> queries, long threadId) {
    Set<RecipientId> recipientIds = new HashSet<>();
    for (String query : queries) {
      for (Recipient recipient : recipientTable.queryRecipientsForMentions(query)) {
        recipientIds.add(recipient.getId());
      }
    }

    Map<Long, List<Mention>> mentionQueryResults = mentionTable.getMentionsContainingRecipients(recipientIds, threadId, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageTable.Reader reader = messageTable.getMessages(mentionQueryResults.keySet())) {
      for (MessageRecord record : reader) {
        results.add(new MessageResult(record.getToRecipient(), record.getFromRecipient(), record.getBody(), record.getBody(), record.getThreadId(), record.getId(), record.getDateReceived(), true));
      }
    }

    return results;
  }

  private @NonNull CharSequence makeSnippet(@NonNull List<String> queries, @NonNull CharSequence styledBody) {
    if (styledBody.length() < 50) {
      return styledBody;
    }

    String lowerBody  = styledBody.toString().toLowerCase();
    for (String query : queries) {
      int foundIndex = lowerBody.indexOf(query.toLowerCase());
      if (foundIndex != -1) {
        int snippetStart = Math.max(0, Math.max(TextUtils.lastIndexOf(styledBody,' ', foundIndex - 5) + 1, foundIndex - 15));
        int lastSpace    = TextUtils.indexOf(styledBody, ' ', foundIndex + 30);
        int snippetEnd   = Math.min(styledBody.length(), lastSpace > 0 ? Math.min(lastSpace, foundIndex + 40) : foundIndex + 40);

        return new SpannableStringBuilder().append(snippetStart > 0 ? SNIPPET_WRAP : "")
                                           .append(styledBody.subSequence(snippetStart, snippetEnd))
                                           .append(snippetEnd < styledBody.length() ? SNIPPET_WRAP : "");
      }
    }

    return styledBody;
  }

  private @NonNull <T> List<T> readToList(@Nullable Cursor cursor, @NonNull ModelBuilder<T> builder) {
    return readToList(cursor, builder, -1);
  }

  private @NonNull <T> List<T> readToList(@Nullable Cursor cursor, @NonNull ModelBuilder<T> builder, int limit) {
    if (cursor == null) {
      return Collections.emptyList();
    }

    int     i    = 0;
    List<T> list = new ArrayList<>(cursor.getCount());

    while (cursor.moveToNext() && (limit < 0 || i < limit)) {
      list.add(builder.build(cursor));
      i++;
    }

    return list;
  }

  private @NonNull List<String> convertMentionsQueryToTokens(@NonNull String query) {
    String[] parts = query.split("\\s+");
    if (parts.length > 3) {
      return Collections.emptyList();
    } else {
      return Arrays.asList(parts);
    }
  }

  private static @NonNull List<MessageResult> mergeMessagesAndMentions(@NonNull List<MessageResult> messages, @NonNull List<MessageResult> mentionMessages) {
    Set<Long> includedMmsMessages = new HashSet<>();

    List<MessageResult> combined = new ArrayList<>(messages.size() + mentionMessages.size());
    for (MessageResult result : messages) {
      combined.add(result);
      if (result.isMms()) {
        includedMmsMessages.add(result.getMessageId());
      }
    }

    for (MessageResult result : mentionMessages) {
      if (!includedMmsMessages.contains(result.getMessageId())) {
        combined.add(result);
      }
    }

    Collections.sort(combined, Collections.reverseOrder((left, right) -> Long.compare(left.getReceivedTimestampMs(), right.getReceivedTimestampMs())));

    return combined;
  }

  private static class RecipientModelBuilder implements ModelBuilder<Recipient> {

    @Override
    public Recipient build(@NonNull Cursor cursor) {
      long recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN));
      return Recipient.resolved(RecipientId.from(recipientId));
    }
  }

  private static class ThreadModelBuilder implements ModelBuilder<ThreadRecord> {

    private final ThreadTable threadTable;

    ThreadModelBuilder(@NonNull ThreadTable threadTable) {
      this.threadTable = threadTable;
    }

    @Override
    public ThreadRecord build(@NonNull Cursor cursor) {
      return threadTable.readerFor(cursor).getCurrent();
    }
  }

  private static class MessageModelBuilder implements ModelBuilder<MessageResult> {

    @Override
    public MessageResult build(@NonNull Cursor cursor) {
      RecipientId conversationRecipientId = RecipientId.from(CursorUtil.requireLong(cursor, SearchTable.CONVERSATION_RECIPIENT));
      RecipientId messageRecipientId      = RecipientId.from(CursorUtil.requireLong(cursor, SearchTable.MESSAGE_RECIPIENT));
      Recipient   conversationRecipient   = Recipient.live(conversationRecipientId).get();
      Recipient   messageRecipient        = Recipient.live(messageRecipientId).get();
      String      body                    = CursorUtil.requireString(cursor, SearchTable.BODY);
      String      bodySnippet             = CursorUtil.requireString(cursor, SearchTable.SNIPPET);
      long        receivedMs              = CursorUtil.requireLong(cursor, MessageTable.DATE_RECEIVED);
      long        threadId                = CursorUtil.requireLong(cursor, MessageTable.THREAD_ID);
      int         messageId               = CursorUtil.requireInt(cursor, SearchTable.MESSAGE_ID);
      boolean     isMms                   = CursorUtil.requireInt(cursor, SearchTable.IS_MMS) == 1;

      return new MessageResult(conversationRecipient, messageRecipient, body, bodySnippet, threadId, messageId, receivedMs, isMms);
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }

  public interface ModelBuilder<T> {
    T build(@NonNull Cursor cursor);
  }
}
