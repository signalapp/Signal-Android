package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;

import java.util.List;

public class ConversationListLoader extends CursorLoader {

  private final String filter;
  private final Context context;

  public ConversationListLoader(Context context, String filter) {
    super(context);
    this.filter  = filter;
    this.context = context.getApplicationContext();
  }

  @Override
  public Cursor loadInBackground() {
    if (filter != null && filter.trim().length() != 0) {
      List<String> numbers = ContactAccessor.getInstance()
          .getNumbersForThreadSearchFilter(filter, context.getContentResolver());

      return DatabaseFactory.getThreadDatabase(context).getFilteredConversationList(numbers);
    } else {
      return DatabaseFactory.getThreadDatabase(context).getConversationList();
    }
  }
}
