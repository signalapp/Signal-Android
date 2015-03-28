package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

import java.util.List;

public class ConversationListLoader extends AbstractCursorLoader {

  private final String filter;
  private final Boolean viewInbox;

  public ConversationListLoader(Context context, String filter, Boolean viewInbox) {
    super(context);
    this.filter = filter;
    this.viewInbox = viewInbox;
  }

  @Override
  public Cursor getCursor() {
    if (filter != null && filter.trim().length() != 0) {
      List<String> numbers = ContactAccessor.getInstance().getNumbersForThreadSearchFilter(context, filter);

      return DatabaseFactory.getThreadDatabase(context).getFilteredConversationList(numbers);
    } else {
      if(viewInbox) {
        return DatabaseFactory.getThreadDatabase(context).getInboxConversationList();
      } else {
        return DatabaseFactory.getThreadDatabase(context).getArchivedConversationList();
      }
    }
  }
}
