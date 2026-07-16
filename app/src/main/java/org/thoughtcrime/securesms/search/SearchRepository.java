package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
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
import org.thoughtcrime.securesms.database.model.ThreadWithRecipient;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SignalTrace;
import org.signal.core.util.Util;
import org.signal.core.util.concurrent.SerialExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
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

  private static final int MAX_SNIPPET_SIZE = 100;

  private final Context           context;
  private final String            noteToSelfTitle;
  private final SearchTable       searchDatabase;
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
    this.serialExecutor    = new SerialExecutor(SignalExecutors.BOUNDED);
  }

  @WorkerThread
  public @NonNull ThreadSearchResult queryThreadsSync(@NonNull String query, boolean unreadOnly) {
    SignalTrace.beginSection("ConversationListSearch-Threads");
    try {
      long                      start  = System.currentTimeMillis();
      List<ThreadWithRecipient> result = queryConversations(query, unreadOnly);

      Log.d(TAG, "[threads] Search took " + (System.currentTimeMillis() - start) + " ms");

      return new ThreadSearchResult(result, query);
    } finally {
      SignalTrace.endSection();
    }
  }

  @WorkerThread
  public @NonNull MessageSearchResult queryMessagesSync(@NonNull String query, @NonNull SearchFilter filter) {
    SignalTrace.beginSection("ConversationListSearch-Messages");
    try {
      long start = System.currentTimeMillis();

      List<MessageResult> messages         = queryMessages(query, filter);
      List<MessageResult> mentionMessages  = queryMentions(convertMentionsQueryToTokens(query));
      List<MessageResult> filteredMentions = filterMentionResults(mentionMessages, filter);
      List<MessageResult> combined         = mergeMessagesAndMentions(messages, filteredMentions);

      Log.d(TAG, "[messages] Search took " + (System.currentTimeMillis() - start) + " ms");

      return new MessageSearchResult(combined, query);
    } finally {
      SignalTrace.endSection();
    }
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

  private @NonNull List<ThreadWithRecipient> queryConversations(@NonNull String query, boolean unreadOnly) {
    if (Util.isEmpty(query)) {
      return Collections.emptyList();
    }

    Set<RecipientId> filteredContacts = new LinkedHashSet<>();
    try (Cursor cursor = SignalDatabase.recipients().queryAllContacts(query, RecipientTable.IncludeSelfMode.IncludeWithoutRemap.INSTANCE)) {
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

    LinkedHashSet<ThreadWithRecipient> output = new LinkedHashSet<>();

    output.addAll(getMatchingThreads(contactIds, unreadOnly));
    output.addAll(getMatchingThreads(groupsByTitleIds, unreadOnly));

    return new ArrayList<>(output);
  }

  private List<ThreadWithRecipient> getMatchingThreads(@NonNull Collection<RecipientId> recipientIds, boolean unreadOnly) {
    try (Cursor cursor = threadTable.getFilteredConversationList(new ArrayList<>(recipientIds), unreadOnly)) {
      return readToList(cursor, new ThreadModelBuilder(threadTable));
    }
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query, @NonNull SearchFilter filter) {
    if (Util.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<MessageResult> results;
    try (Cursor cursor = searchDatabase.queryMessages(query, filter)) {
      results = readToList(cursor, new MessageModelBuilder());
    }

    if (results.isEmpty()) {
      return results;
    }

    List<String> snippetQueries = tokenizeQuery(query);

    List<Long> messageIds = new LinkedList<>();
    for (MessageResult result : results) {
      if (result.isMms()) {
        messageIds.add(result.getMessageId());
      }
    }

    Map<Long, BodyRangeList> bodyRanges = messageIds.isEmpty() ? Collections.emptyMap() : SignalDatabase.messages().getBodyRangesForMessages(messageIds);
    Map<Long, List<Mention>> mentions   = messageIds.isEmpty() ? Collections.emptyMap() : SignalDatabase.mentions().getMentionsForMessages(messageIds);

    List<MessageResult> updatedResults = new ArrayList<>(results.size());
    for (MessageResult result : results) {
      CharSequence         updatedBody     = result.getBody();
      List<BodyAdjustment> bodyAdjustments = Collections.emptyList();
      List<Mention>        messageMentions = mentions.get(result.getMessageId());
      BodyRangeList        ranges          = bodyRanges.get(result.getMessageId());

      if (messageMentions != null) {
        MentionUtil.UpdatedBodyAndMentions bodyMentionUpdate = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, updatedBody, messageMentions);
        updatedBody     = Objects.requireNonNull(bodyMentionUpdate.getBody());
        bodyAdjustments = bodyMentionUpdate.getBodyAdjustments();
      }

      if (ranges != null) {
        updatedBody = SpannableString.valueOf(updatedBody);
        MessageStyler.style(result.getReceivedTimestampMs(), BodyRangeUtil.adjustBodyRanges(ranges, bodyAdjustments), (Spannable) updatedBody);
      }

      CharSequence updatedSnippet = makeSnippet(snippetQueries, updatedBody);

      updatedResults.add(new MessageResult(result.getConversationRecipient(), result.getMessageRecipient(), updatedBody, updatedSnippet, result.getThreadId(), result.getMessageId(), result.getReceivedTimestampMs(), result.isMms()));
    }

    return updatedResults;
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query, long threadId) {
    List<MessageResult> results;
    try (Cursor cursor = searchDatabase.queryMessages(query, threadId)) {
      results = readToList(cursor, new MessageModelBuilder());
    }

    if (results.isEmpty()) {
      return results;
    }

    List<String>        snippetQueries = tokenizeQuery(query);
    List<MessageResult> updatedResults = new ArrayList<>(results.size());
    for (MessageResult result : results) {
      CharSequence snippet = makeSnippet(snippetQueries, result.getBody());
      updatedResults.add(new MessageResult(result.getConversationRecipient(), result.getMessageRecipient(), result.getBody(), snippet, result.getThreadId(), result.getMessageId(), result.getReceivedTimestampMs(), result.isMms()));
    }

    return updatedResults;
  }

  @VisibleForTesting
  static @NonNull List<String> tokenizeQuery(@NonNull String query) {
    List<String> tokens = new ArrayList<>();
    for (String part : query.split("\\s+")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        tokens.add(trimmed);
      }
    }
    return tokens;
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

  @VisibleForTesting
  static @NonNull CharSequence makeSnippet(@NonNull List<String> queries, @NonNull CharSequence styledBody) {
    if (styledBody.length() < 50) {
      return styledBody;
    }

    String lowerBody  = styledBody.toString().toLowerCase(Locale.ROOT);
    for (String query : queries) {
      int foundIndex = lowerBody.indexOf(query.toLowerCase(Locale.ROOT));
      if (foundIndex != -1) {
        int snippetStart = Math.max(0, Math.max(TextUtils.lastIndexOf(styledBody,' ', foundIndex - 5) + 1, foundIndex - 15));
        int lastSpace    = TextUtils.indexOf(styledBody, ' ', foundIndex + 30);
        int snippetEnd   = Math.min(styledBody.length(), lastSpace > 0 ? Math.min(lastSpace, foundIndex + 40) : foundIndex + 40);

        return new SpannableStringBuilder().append(snippetStart > 0 ? SNIPPET_WRAP : "")
                                           .append(styledBody.subSequence(snippetStart, snippetEnd))
                                           .append(snippetEnd < styledBody.length() ? SNIPPET_WRAP : "");
      }
    }

    if (styledBody.length() <= MAX_SNIPPET_SIZE) {
      return styledBody;
    }

    int lastSpace = TextUtils.lastIndexOf(styledBody, ' ', MAX_SNIPPET_SIZE);
    int snippetEnd = lastSpace > 0 ? lastSpace : MAX_SNIPPET_SIZE;

    return new SpannableStringBuilder().append(styledBody.subSequence(0, snippetEnd))
                                       .append(SNIPPET_WRAP);
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

  private static @NonNull List<MessageResult> filterMentionResults(@NonNull List<MessageResult> mentions, @NonNull SearchFilter filter) {
    if (filter.isEmpty()) {
      return mentions;
    }

    List<MessageResult> filtered = new ArrayList<>();
    for (MessageResult mention : mentions) {
      if (filter.getStartDate() != null && mention.getReceivedTimestampMs() < filter.getStartDate()) {
        continue;
      }
      if (filter.getEndDate() != null && mention.getReceivedTimestampMs() > filter.getEndDate()) {
        continue;
      }
      if (filter.getAuthor() != null && !mention.getMessageRecipient().getId().equals(filter.getAuthor())) {
        continue;
      }
      filtered.add(mention);
    }

    return filtered;
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

  private static class ThreadModelBuilder implements ModelBuilder<ThreadWithRecipient> {

    private final ThreadTable threadTable;

    ThreadModelBuilder(@NonNull ThreadTable threadTable) {
      this.threadTable = threadTable;
    }

    @Override
    public ThreadWithRecipient build(@NonNull Cursor cursor) {
      return threadTable.readerFor(cursor).getCurrent();
    }
  }

  private static class MessageModelBuilder implements ModelBuilder<MessageResult> {

    @Override
    public MessageResult build(@NonNull Cursor cursor) {
      RecipientId conversationRecipientId = RecipientId.from(CursorUtil.requireLong(cursor, SearchTable.CONVERSATION_RECIPIENT));
      RecipientId messageRecipientId      = RecipientId.from(CursorUtil.requireLong(cursor, SearchTable.MESSAGE_RECIPIENT));
      Recipient   conversationRecipient   = Recipient.resolved(conversationRecipientId);
      Recipient   messageRecipient        = Recipient.resolved(messageRecipientId);
      String      body                    = CursorUtil.requireString(cursor, SearchTable.BODY);
      long        receivedMs              = CursorUtil.requireLong(cursor, MessageTable.DATE_RECEIVED);
      long        threadId                = CursorUtil.requireLong(cursor, MessageTable.THREAD_ID);
      int         messageId               = CursorUtil.requireInt(cursor, SearchTable.MESSAGE_ID);
      boolean     isMms                   = CursorUtil.requireInt(cursor, SearchTable.IS_MMS) == 1;

      if (body == null) {
        body = "";
      }

      return new MessageResult(conversationRecipient, messageRecipient, body, body, threadId, messageId, receivedMs, isMms);
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }

  public interface ModelBuilder<T> {
    T build(@NonNull Cursor cursor);
  }
}
