package org.thoughtcrime.securesms.search;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;

import com.annimon.stream.Stream;


import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactRepository;
import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SearchDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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

  public SearchRepository() {
    this.context           = ApplicationDependencies.getApplication().getApplicationContext();
    this.searchDatabase    = DatabaseFactory.getSearchDatabase(context);
    this.threadDatabase    = DatabaseFactory.getThreadDatabase(context);
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

      Future<List<Recipient>>     contacts      = parallelExecutor.submit(() -> queryContacts(cleanQuery));
      Future<List<ThreadRecord>>  conversations = parallelExecutor.submit(() -> queryConversations(cleanQuery));
      Future<List<MessageResult>> messages      = parallelExecutor.submit(() -> queryMessages(cleanQuery));

      try {
        long         startTime = System.currentTimeMillis();
        SearchResult result    = new SearchResult(cleanQuery, contacts.get(), conversations.get(), messages.get());

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
      long startTime = System.currentTimeMillis();
      List<MessageResult> messages = queryMessages(sanitizeQuery(query), threadId);
      Log.d(TAG, "[ConversationQuery] " + (System.currentTimeMillis() - startTime) + " ms");

      callback.onResult(messages);
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
    try (Cursor cursor = searchDatabase.queryMessages(query)) {
      return readToList(cursor, new MessageModelBuilder(context));
    }
  }

  private @NonNull List<MessageResult> queryMessages(@NonNull String query, long threadId) {
    try (Cursor cursor = searchDatabase.queryMessages(query, threadId)) {
      return readToList(cursor, new MessageModelBuilder(context));
    }
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

    private final Context context;

    MessageModelBuilder(@NonNull Context context) {
      this.context = context;
    }

    @Override
    public MessageResult build(@NonNull Cursor cursor) {
      RecipientId conversationRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndex(SearchDatabase.CONVERSATION_RECIPIENT)));
      RecipientId messageRecipientId      = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(SearchDatabase.MESSAGE_RECIPIENT)));
      Recipient   conversationRecipient   = Recipient.live(conversationRecipientId).get();
      Recipient   messageRecipient        = Recipient.live(messageRecipientId).get();
      String      body                    = cursor.getString(cursor.getColumnIndexOrThrow(SearchDatabase.SNIPPET));
      long        receivedMs              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));
      long        threadId                = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.THREAD_ID));

      return new MessageResult(conversationRecipient, messageRecipient, body, threadId, receivedMs);
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }
}
