package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.concurrent.LatestPrioritizedSerialExecutor;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.ContactRepository;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MentionTable;
import org.thoughtcrime.securesms.database.MentionUtil;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SearchTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FtsUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
  private final MentionTable   mentionDatabase;
  private final MessageTable mmsDatabase;

  private final LatestPrioritizedSerialExecutor searchExecutor;
  private final Executor                        serialExecutor;

  public SearchRepository(@NonNull String noteToSelfTitle) {
    this.context           = ApplicationDependencies.getApplication().getApplicationContext();
    this.noteToSelfTitle   = noteToSelfTitle;
    this.searchDatabase = SignalDatabase.messageSearch();
    this.threadTable    = SignalDatabase.threads();
    this.recipientTable = SignalDatabase.recipients();
    this.mentionDatabase = SignalDatabase.mentions();
    this.mmsDatabase       = SignalDatabase.mms();
    this.contactRepository = new ContactRepository(context, noteToSelfTitle);
    this.searchExecutor    = new LatestPrioritizedSerialExecutor(SignalExecutors.BOUNDED);
    this.serialExecutor    = new SerialExecutor(SignalExecutors.BOUNDED);
  }

  public void queryThreads(@NonNull String query, @NonNull Consumer<ThreadSearchResult> callback) {
    searchExecutor.execute(2, () -> {
      long               start  = System.currentTimeMillis();
      List<ThreadRecord> result = queryConversations(query);

      Log.d(TAG, "[threads] Search took " + (System.currentTimeMillis() - start) + " ms");

      callback.accept(new ThreadSearchResult(result, query));
    });
  }

  public void queryContacts(@NonNull String query, @NonNull Consumer<ContactSearchResult> callback) {
    searchExecutor.execute(1, () -> {
      long            start  = System.currentTimeMillis();
      List<Recipient> result = queryContacts(query);

      Log.d(TAG, "[contacts] Search took " + (System.currentTimeMillis() - start) + " ms");

      callback.accept(new ContactSearchResult(result, query));
    });
  }

  public void queryMessages(@NonNull String query, @NonNull Consumer<MessageSearchResult> callback) {
    searchExecutor.execute(0, () -> {
      long   start      = System.currentTimeMillis();
      String cleanQuery = FtsUtil.sanitize(query);

      List<MessageResult> messages        = queryMessages(cleanQuery);
      List<MessageResult> mentionMessages = queryMentions(sanitizeQueryAsTokens(query));
      List<MessageResult> combined        = mergeMessagesAndMentions(messages, mentionMessages);

      Log.d(TAG, "[messages] Search took " + (System.currentTimeMillis() - start) + " ms");

      callback.accept(new MessageSearchResult(combined, query));
    });
  }

  public void query(@NonNull String query, long threadId, @NonNull Callback<List<MessageResult>> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(Collections.emptyList());
      return;
    }

    serialExecutor.execute(() -> {
      long                startTime       = System.currentTimeMillis();
      List<MessageResult> messages        = queryMessages(FtsUtil.sanitize(query), threadId);
      List<MessageResult> mentionMessages = queryMentions(sanitizeQueryAsTokens(query), threadId);

      Log.d(TAG, "[ConversationQuery] " + (System.currentTimeMillis() - startTime) + " ms");

      callback.onResult(mergeMessagesAndMentions(messages, mentionMessages));
    });
  }

  private List<Recipient> queryContacts(String query) {
    if (Util.isEmpty(query)) {
      return Collections.emptyList();
    }

    Cursor contacts = null;

    try {
      Cursor textSecureContacts = contactRepository.querySignalContacts(query);
      Cursor systemContacts     = contactRepository.queryNonSignalContacts(query);

      contacts = new MergeCursor(new Cursor[]{ textSecureContacts, systemContacts });

      return readToList(contacts, new RecipientModelBuilder(), 250);
    } finally {
      if (contacts != null) {
        contacts.close();
      }
    }
  }

  private @NonNull List<ThreadRecord> queryConversations(@NonNull String query) {
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

    GroupTable.GroupRecord record;
    try (GroupTable.Reader reader = SignalDatabase.groups().queryGroupsByTitle(query, true, false, false)) {
      while ((record = reader.getNext()) != null) {
        groupsByTitleIds.add(record.getRecipientId());
      }
    }

    Set<RecipientId> groupsByMemberIds = new LinkedHashSet<>();

    try (GroupTable.Reader reader = SignalDatabase.groups().queryGroupsByMembership(filteredContacts, true, false, false)) {
      while ((record = reader.getNext()) != null) {
        groupsByMemberIds.add(record.getRecipientId());
      }
    }

    List<ThreadRecord> output = new ArrayList<>(contactIds.size() + groupsByTitleIds.size() + groupsByMemberIds.size());

    output.addAll(getMatchingThreads(contactIds));
    output.addAll(getMatchingThreads(groupsByTitleIds));
    output.addAll(getMatchingThreads(groupsByMemberIds));

    return output;
  }

  private List<ThreadRecord> getMatchingThreads(@NonNull Collection<RecipientId> recipientIds) {
    try (Cursor cursor = threadTable.getFilteredConversationList(new ArrayList<>(recipientIds))) {
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

    Map<Long, List<Mention>> mentions = SignalDatabase.mentions().getMentionsForMessages(messageIds);
    if (mentions.isEmpty()) {
      return results;
    }

    List<MessageResult> updatedResults = new ArrayList<>(results.size());
    for (MessageResult result : results) {
      if (result.isMms() && mentions.containsKey(result.getMessageId())) {
        List<Mention> messageMentions = mentions.get(result.getMessageId());

        //noinspection ConstantConditions
        String updatedBody    = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, result.getBody(), messageMentions).getBody().toString();
        String updatedSnippet = updateSnippetWithDisplayNames(result.getBody(), result.getBodySnippet(), messageMentions);

        //noinspection ConstantConditions
        updatedResults.add(new MessageResult(result.getConversationRecipient(), result.getMessageRecipient(), updatedBody, updatedSnippet, result.getThreadId(), result.getMessageId(), result.getReceivedTimestampMs(), result.isMms()));
      } else {
        updatedResults.add(result);
      }
    }

    return updatedResults;
  }

  private @NonNull String updateSnippetWithDisplayNames(@NonNull String body, @NonNull String bodySnippet, @NonNull List<Mention> mentions) {
    String cleanSnippet = bodySnippet;
    int    startOffset  = 0;

    if (cleanSnippet.startsWith(SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.substring(SNIPPET_WRAP.length());
      startOffset  = SNIPPET_WRAP.length();
    }

    if (cleanSnippet.endsWith(SNIPPET_WRAP)) {
      cleanSnippet = cleanSnippet.substring(0, cleanSnippet.length() - SNIPPET_WRAP.length());
    }

    int startIndex = body.indexOf(cleanSnippet);

    if (startIndex != -1) {
      List<Mention> adjustMentions = new ArrayList<>(mentions.size());
      for (Mention mention : mentions) {
        int adjustedStart = mention.getStart() - startIndex + startOffset;
        if (adjustedStart >= 0 && adjustedStart + mention.getLength() <= cleanSnippet.length()) {
          adjustMentions.add(new Mention(mention.getRecipientId(), adjustedStart, mention.getLength()));
        }
      }

      //noinspection ConstantConditions
      return MentionUtil.updateBodyAndMentionsWithDisplayNames(context, bodySnippet, adjustMentions).getBody().toString();
    }

    return bodySnippet;
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

    Map<Long, List<Mention>> mentionQueryResults = mentionDatabase.getMentionsContainingRecipients(recipientIds, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageTable.Reader reader = mmsDatabase.getMessages(mentionQueryResults.keySet())) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        List<Mention> mentions = mentionQueryResults.get(record.getId());
        if (Util.hasItems(mentions)) {
          MentionUtil.UpdatedBodyAndMentions updated        = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, record.getBody(), mentions);
          String                             updatedBody    = updated.getBody() != null ? updated.getBody().toString() : record.getBody();
          String                             updatedSnippet = makeSnippet(cleanQueries, updatedBody);

          //noinspection ConstantConditions
          results.add(new MessageResult(threadTable.getRecipientForThreadId(record.getThreadId()), record.getRecipient(), updatedBody, updatedSnippet, record.getThreadId(), record.getId(), record.getDateReceived(), true));
        }
      }
    }

    return results;
  }

  private @NonNull List<MessageResult> queryMentions(@NonNull List<String> cleanQueries, long threadId) {
    Set<RecipientId> recipientIds = new HashSet<>();
    for (String cleanQuery : cleanQueries) {
      for (Recipient recipient : recipientTable.queryRecipientsForMentions(cleanQuery)) {
        recipientIds.add(recipient.getId());
      }
    }

    Map<Long, List<Mention>> mentionQueryResults = mentionDatabase.getMentionsContainingRecipients(recipientIds, threadId, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageTable.Reader reader = mmsDatabase.getMessages(mentionQueryResults.keySet())) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        //noinspection ConstantConditions
        results.add(new MessageResult(threadTable.getRecipientForThreadId(record.getThreadId()), record.getRecipient(), record.getBody(), record.getBody(), record.getThreadId(), record.getId(), record.getDateReceived(), true));
      }
    }

    return results;
  }

  private @NonNull String makeSnippet(@NonNull List<String> queries, @NonNull String body) {
    if (body.length() < 50) {
      return body;
    }

    String lowerBody = body.toLowerCase();
    for (String query : queries) {
      int foundIndex = lowerBody.indexOf(query.toLowerCase());
      if (foundIndex != -1) {
        int snippetStart = Math.max(0, Math.max(body.lastIndexOf(' ', foundIndex - 5) + 1, foundIndex - 15));
        int lastSpace    = body.indexOf(' ', foundIndex + 30);
        int snippetEnd   = Math.min(body.length(), lastSpace > 0 ? Math.min(lastSpace, foundIndex + 40) : foundIndex + 40);

        return (snippetStart > 0 ? SNIPPET_WRAP : "") + body.substring(snippetStart, snippetEnd) + (snippetEnd < body.length() ? SNIPPET_WRAP : "");
      }
    }
    return body;
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

  private @NonNull List<String> sanitizeQueryAsTokens(@NonNull String query) {
    String[] parts = query.split("\\s+");
    if (parts.length > 3) {
      return Collections.emptyList();
    }

    return Stream.of(parts).map(FtsUtil::sanitize).toList();
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
      long        receivedMs              = CursorUtil.requireLong(cursor, MmsSmsColumns.NORMALIZED_DATE_RECEIVED);
      long        threadId                = CursorUtil.requireLong(cursor, MmsSmsColumns.THREAD_ID);
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
