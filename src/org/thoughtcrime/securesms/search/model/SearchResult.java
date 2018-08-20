package org.thoughtcrime.securesms.search.model;

import android.database.ContentObserver;
import android.database.DataSetObserver;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

/**
 * Represents an all-encompassing search result that can contain various result for different
 * subcategories.
 */
public class SearchResult {

  public static final SearchResult EMPTY = new SearchResult("", CursorList.emptyList(), CursorList.emptyList(), CursorList.emptyList());

  private final String                    query;
  private final CursorList<Recipient>     contacts;
  private final CursorList<ThreadRecord>  conversations;
  private final CursorList<MessageResult> messages;

  public SearchResult(@NonNull String                    query,
                      @NonNull CursorList<Recipient>     contacts,
                      @NonNull CursorList<ThreadRecord>  conversations,
                      @NonNull CursorList<MessageResult> messages)
  {
    this.query         = query;
    this.contacts      = contacts;
    this.conversations = conversations;
    this.messages      = messages;
  }

  public List<Recipient> getContacts() {
    return contacts;
  }

  public List<ThreadRecord> getConversations() {
    return conversations;
  }

  public List<MessageResult> getMessages() {
    return messages;
  }

  public String getQuery() {
    return query;
  }

  public int size() {
    return contacts.size() + conversations.size() + messages.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public void registerContentObserver(@NonNull ContentObserver observer) {
    contacts.registerContentObserver(observer);
    conversations.registerContentObserver(observer);
    messages.registerContentObserver(observer);
  }

  public void unregisterContentObserver(@NonNull ContentObserver observer) {
    contacts.unregisterContentObserver(observer);
    conversations.unregisterContentObserver(observer);
    messages.unregisterContentObserver(observer);
  }

  public void close() {
    contacts.close();
    conversations.close();
    messages.close();
  }
}
