package org.thoughtcrime.securesms.conversationlist.model;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.List;

/**
 * Represents an all-encompassing search result that can contain various result for different
 * subcategories.
 */
public class SearchResult {

  public static final SearchResult EMPTY = new SearchResult("", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

  private final String              query;
  private final List<Recipient>     contacts;
  private final List<ThreadRecord>  conversations;
  private final List<MessageResult> messages;

  public SearchResult(@NonNull String              query,
                      @NonNull List<Recipient>     contacts,
                      @NonNull List<ThreadRecord>  conversations,
                      @NonNull List<MessageResult> messages)
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
}
