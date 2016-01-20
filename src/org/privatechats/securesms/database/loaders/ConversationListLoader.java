package org.privatechats.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import org.privatechats.securesms.contacts.ContactAccessor;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.database.ThreadDatabase;
import org.privatechats.securesms.util.AbstractCursorLoader;

import java.util.LinkedList;
import java.util.List;

public class ConversationListLoader extends AbstractCursorLoader {

  private final String filter;
  private final boolean archived;

  public ConversationListLoader(Context context, String filter, boolean archived) {
    super(context);
    this.filter   = filter;
    this.archived = archived;
  }

  @Override
  public Cursor getCursor() {
    if      (filter != null && filter.trim().length() != 0) return getFilteredConversationList(filter);
    else if (!archived)                                     return getUnarchivedConversationList();
    else                                                    return getArchivedConversationList();
  }

  private Cursor getUnarchivedConversationList() {
    List<Cursor> cursorList = new LinkedList<>();
    cursorList.add(DatabaseFactory.getThreadDatabase(context).getConversationList());

    int archivedCount = DatabaseFactory.getThreadDatabase(context)
                                       .getArchivedConversationListCount();

    if (archivedCount > 0) {
      MatrixCursor switchToArchiveCursor = new MatrixCursor(new String[] {
          ThreadDatabase.ID, ThreadDatabase.DATE, ThreadDatabase.MESSAGE_COUNT,
          ThreadDatabase.RECIPIENT_IDS, ThreadDatabase.SNIPPET, ThreadDatabase.READ,
          ThreadDatabase.TYPE, ThreadDatabase.SNIPPET_TYPE, ThreadDatabase.SNIPPET_URI,
          ThreadDatabase.ARCHIVED, ThreadDatabase.STATUS, ThreadDatabase.RECEIPT_COUNT}, 1);

      switchToArchiveCursor.addRow(new Object[] {-1L, System.currentTimeMillis(), archivedCount,
                                                 "-1", null, 1, ThreadDatabase.DistributionTypes.ARCHIVE,
                                                 0, null, 0, -1, 0});

      cursorList.add(switchToArchiveCursor);
    }

    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }

  private Cursor getArchivedConversationList() {
    return DatabaseFactory.getThreadDatabase(context).getArchivedConversationList();
  }

  private Cursor getFilteredConversationList(String filter) {
    List<String> numbers = ContactAccessor.getInstance().getNumbersForThreadSearchFilter(context, filter);
    return DatabaseFactory.getThreadDatabase(context).getFilteredConversationList(numbers);
  }
}
