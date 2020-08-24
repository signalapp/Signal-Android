package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactRepository;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MentionDatabase;
import org.thoughtcrime.securesms.database.MentionUtil;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.thoughtcrime.securesms.database.SearchDatabase.SNIPPET_WRAP;

/**
 * Manages data retrieval for search.
 */
public class SearchRepository {

  private static final String TAG = SearchRepository.class.getSimpleName();

  private static final Set<Character> BANNED_CHARACTERS = new HashSet<>();
  static {
    // Several ranges of invalid ASCII characters
    for (int i = 33; i <= 47; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 58; i <= 64; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 91; i <= 96; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
    for (int i = 123; i <= 126; i++) {
      BANNED_CHARACTERS.add((char) i);
    }
  }

  private final Context           context;
  private final SearchDatabase    searchDatabase;
  private final ContactRepository contactRepository;
  private final ThreadDatabase    threadDatabase;
  private final ContactAccessor   contactAccessor;
  private final Executor          serialExecutor;
  private final ExecutorService   parallelExecutor;
  private final RecipientDatabase recipientDatabase;
  private final MentionDatabase   mentionDatabase;
  private final MessageDatabase   mmsDatabase;

  public SearchRepository() {
    this.context           = ApplicationDependencies.getApplication().getApplicationContext();
    this.searchDatabase    = DatabaseFactory.getSearchDatabase(context);
    this.threadDatabase    = DatabaseFactory.getThreadDatabase(context);
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.mentionDatabase   = DatabaseFactory.getMentionDatabase(context);
    this.mmsDatabase       = DatabaseFactory.getMmsDatabase(context);
    this.contactRepository = new ContactRepository(context);
    this.contactAccessor   = ContactAccessor.getInstance();
    this.serialExecutor    = SignalExecutors.SERIAL;
    this.parallelExecutor  = SignalExecutors.BOUNDED;
  }

  public void query(@NonNull String query, @NonNull Callback<SearchResult> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(SearchResult.EMPTY);
      return;
    }

    serialExecutor.execute(() -> {
      String cleanQuery = sanitizeQuery(query);

      Future<List<Recipient>>     contacts        = parallelExecutor.submit(() -> queryContacts(cleanQuery));
      Future<List<ThreadRecord>>  conversations   = parallelExecutor.submit(() -> queryConversations(cleanQuery));
      Future<List<MessageResult>> messages        = parallelExecutor.submit(() -> queryMessages(cleanQuery));
      Future<List<MessageResult>> mentionMessages = parallelExecutor.submit(() -> queryMentions(sanitizeQueryAsTokens(query)));

      try {
        long         startTime = System.currentTimeMillis();
        SearchResult result    = new SearchResult(cleanQuery, contacts.get(), conversations.get(), mergeMessagesAndMentions(messages.get(), mentionMessages.get()));

        Log.d(TAG, "Total time: " + (System.currentTimeMillis() - startTime) + " ms");

        callback.onResult(result);
      } catch (ExecutionException | InterruptedException e) {
        Log.w(TAG, e);
        callback.onResult(SearchResult.EMPTY);
      }
    });
  }

  public void query(@NonNull String query, long threadId, @NonNull Callback<List<MessageResult>> callback) {
    if (TextUtils.isEmpty(query)) {
      callback.onResult(CursorList.emptyList());
      return;
    }

    serialExecutor.execute(() -> {
      long                startTime       = System.currentTimeMillis();
      List<MessageResult> messages        = queryMessages(sanitizeQuery(query), threadId);
      List<MessageResult> mentionMessages = queryMentions(sanitizeQueryAsTokens(query), threadId);

      Log.d(TAG, "[ConversationQuery] " + (System.currentTimeMillis() - startTime) + " ms");

      callback.onResult(mergeMessagesAndMentions(messages, mentionMessages));
    });
  }

  private List<Recipient> queryContacts(String query) {
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
    List<String>      numbers      = contactAccessor.getNumbersForThreadSearchFilter(context, query);
    List<RecipientId> recipientIds = Stream.of(numbers).map(number -> Recipient.external(context, number)).map(Recipient::getId).toList();

    try (Cursor cursor = threadDatabase.getFilteredConversationList(recipientIds)) {
      return readToList(cursor, new ThreadModelBuilder(threadDatabase));
    }
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query) {
    List<MessageResult> results;
    try (Cursor cursor = searchDatabase.queryMessages(query)) {
      results = readToList(cursor, new MessageModelBuilder());
    }

    List<Long> messageIds = new LinkedList<>();
    for (MessageResult result : results) {
      if (result.isMms) {
        messageIds.add(result.messageId);
      }
    }

    if (messageIds.isEmpty()) {
      return results;
    }

    Map<Long, List<Mention>> mentions = DatabaseFactory.getMentionDatabase(context).getMentionsForMessages(messageIds);
    if (mentions.isEmpty()) {
      return results;
    }

    List<MessageResult> updatedResults = new ArrayList<>(results.size());
    for (MessageResult result : results) {
      if (result.isMms && mentions.containsKey(result.messageId)) {
        List<Mention> messageMentions = mentions.get(result.messageId);

        //noinspection ConstantConditions
        String updatedBody    = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, result.body, messageMentions).getBody().toString();
        String updatedSnippet = updateSnippetWithDisplayNames(result.body, result.bodySnippet, messageMentions);

        //noinspection ConstantConditions
        updatedResults.add(new MessageResult(result.conversationRecipient, result.messageRecipient, updatedBody, updatedSnippet, result.threadId, result.messageId, result.receivedTimestampMs, result.isMms));
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
      for (Recipient recipient : recipientDatabase.queryRecipientsForMentions(cleanQuery)) {
        recipientIds.add(recipient.getId());
      }
    }

    Map<Long, List<Mention>> mentionQueryResults = mentionDatabase.getMentionsContainingRecipients(recipientIds, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageDatabase.Reader reader = mmsDatabase.getMessages(mentionQueryResults.keySet())) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        List<Mention> mentions = mentionQueryResults.get(record.getId());
        if (Util.hasItems(mentions)) {
          MentionUtil.UpdatedBodyAndMentions updated        = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, record.getBody(), mentions);
          String                             updatedBody    = updated.getBody() != null ? updated.getBody().toString() : record.getBody();
          String                             updatedSnippet = makeSnippet(cleanQueries, updatedBody);

          //noinspection ConstantConditions
          results.add(new MessageResult(threadDatabase.getRecipientForThreadId(record.getThreadId()), record.getRecipient(), updatedBody, updatedSnippet, record.getThreadId(), record.getId(), record.getDateReceived(), true));
        }
      }
    }

    return results;
  }

  private @NonNull List<MessageResult> queryMentions(@NonNull List<String> cleanQueries, long threadId) {
    Set<RecipientId> recipientIds = new HashSet<>();
    for (String cleanQuery : cleanQueries) {
      for (Recipient recipient : recipientDatabase.queryRecipientsForMentions(cleanQuery)) {
        recipientIds.add(recipient.getId());
      }
    }

    Map<Long, List<Mention>> mentionQueryResults = mentionDatabase.getMentionsContainingRecipients(recipientIds, threadId, 500);

    if (mentionQueryResults.isEmpty()) {
      return Collections.emptyList();
    }

    List<MessageResult> results = new ArrayList<>();

    try (MessageDatabase.Reader reader = mmsDatabase.getMessages(mentionQueryResults.keySet())) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        //noinspection ConstantConditions
        results.add(new MessageResult(threadDatabase.getRecipientForThreadId(record.getThreadId()), record.getRecipient(), record.getBody(), record.getBody(), record.getThreadId(), record.getId(), record.getDateReceived(), true));
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

  private @NonNull <T> List<T> readToList(@Nullable Cursor cursor, @NonNull CursorList.ModelBuilder<T> builder) {
    return readToList(cursor, builder, -1);
  }

  private @NonNull <T> List<T> readToList(@Nullable Cursor cursor, @NonNull CursorList.ModelBuilder<T> builder, int limit) {
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

  /**
   * Unfortunately {@link DatabaseUtils#sqlEscapeString(String)} is not sufficient for our purposes.
   * MATCH queries have a separate format of their own that disallow most "special" characters.
   *
   * Also, SQLite can't search for apostrophes, meaning we can't normally find words like "I'm".
   * However, if we replace the apostrophe with a space, then the query will find the match.
   */
  private String sanitizeQuery(@NonNull String query) {
    StringBuilder out = new StringBuilder();

    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (!BANNED_CHARACTERS.contains(c)) {
        out.append(c);
      } else if (c == '\'') {
        out.append(' ');
      }
    }

    return out.toString();
  }

  private @NonNull List<String> sanitizeQueryAsTokens(@NonNull String query) {
    String[] parts = query.split("\\s+");
    if (parts.length > 3) {
      return Collections.emptyList();
    }

    return Stream.of(parts).map(this::sanitizeQuery).toList();
  }

  private static @NonNull List<MessageResult> mergeMessagesAndMentions(@NonNull List<MessageResult> messages, @NonNull List<MessageResult> mentionMessages) {
    Set<Long> includedMmsMessages = new HashSet<>();

    List<MessageResult> combined = new ArrayList<>(messages.size() + mentionMessages.size());
    for (MessageResult result : messages) {
      combined.add(result);
      if (result.isMms) {
        includedMmsMessages.add(result.messageId);
      }
    }

    for (MessageResult result : mentionMessages) {
      if (!includedMmsMessages.contains(result.messageId)) {
        combined.add(result);
      }
    }

    Collections.sort(combined, Collections.reverseOrder((left, right) -> Long.compare(left.receivedTimestampMs, right.receivedTimestampMs)));

    return combined;
  }

  private static class RecipientModelBuilder implements CursorList.ModelBuilder<Recipient> {

    @Override
    public Recipient build(@NonNull Cursor cursor) {
      long recipientId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN));
      return Recipient.resolved(RecipientId.from(recipientId));
    }
  }

  private static class ThreadModelBuilder implements CursorList.ModelBuilder<ThreadRecord> {

    private final ThreadDatabase threadDatabase;

    ThreadModelBuilder(@NonNull ThreadDatabase threadDatabase) {
      this.threadDatabase = threadDatabase;
    }

    @Override
    public ThreadRecord build(@NonNull Cursor cursor) {
      return threadDatabase.readerFor(cursor).getCurrent();
    }
  }

  private static class MessageModelBuilder implements CursorList.ModelBuilder<MessageResult> {

    @Override
    public MessageResult build(@NonNull Cursor cursor) {
      RecipientId conversationRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(SearchDatabase.CONVERSATION_RECIPIENT)));
      RecipientId messageRecipientId      = RecipientId.from(CursorUtil.requireLong(cursor, SearchDatabase.MESSAGE_RECIPIENT));
      Recipient   conversationRecipient   = Recipient.live(conversationRecipientId).get();
      Recipient   messageRecipient        = Recipient.live(messageRecipientId).get();
      String      body                    = CursorUtil.requireString(cursor, SearchDatabase.BODY);
      String      bodySnippet             = CursorUtil.requireString(cursor, SearchDatabase.SNIPPET);
      long        receivedMs              = CursorUtil.requireLong(cursor, MmsSmsColumns.NORMALIZED_DATE_RECEIVED);
      long        threadId                = CursorUtil.requireLong(cursor, MmsSmsColumns.THREAD_ID);
      int         messageId               = CursorUtil.requireInt(cursor, SearchDatabase.MESSAGE_ID);
      boolean     isMms                   = CursorUtil.requireInt(cursor, SearchDatabase.IS_MMS) == 1;

      return new MessageResult(conversationRecipient, messageRecipient, body, bodySnippet, threadId, messageId, receivedMs, isMms);
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }
}
