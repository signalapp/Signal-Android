package org.thoughtcrime.securesms.conversationlist.model;

import android.database.Cursor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.CursorUtil;

public class ConversationReader extends ThreadTable.StaticReader {

  public static final String[] HEADER_COLUMN              = { "header" };
  public static final String[] ARCHIVED_COLUMNS           = { "header", "count" };
  public static final String[] FILTER_FOOTER_COLUMNS      = { "header", "show_tip" };
  public static final String[] PINNED_HEADER              = { Conversation.Type.PINNED_HEADER.toString() };
  public static final String[] UNPINNED_HEADER            = { Conversation.Type.UNPINNED_HEADER.toString() };

  public static final long TYPE_NONE     = 0x0;
  public static final long TYPE_SHOW_TIP = 0x1;

  private final Cursor cursor;

  public ConversationReader(@NonNull Cursor cursor) {
    super(cursor, ApplicationDependencies.getApplication());
    this.cursor = cursor;
  }

  public static String[] createArchivedFooterRow(int archivedCount) {
    return new String[]{Conversation.Type.ARCHIVED_FOOTER.toString(), String.valueOf(archivedCount)};
  }

  public static String[] createConversationFilterFooterRow(boolean showTip) {
    return new String[]{Conversation.Type.CONVERSATION_FILTER_FOOTER.toString(), String.valueOf(showTip ? 1 : 0)};
  }

  @Override
  public ThreadRecord getCurrent() {
    if (cursor.getColumnIndex(HEADER_COLUMN[0]) == -1) {
      return super.getCurrent();
    } else {
      return buildThreadRecordForHeader();
    }
  }

  private ThreadRecord buildThreadRecordForHeader() {
    Conversation.Type type  = Conversation.Type.valueOf(CursorUtil.requireString(cursor, HEADER_COLUMN[0]));
    int               count = 0;
    if (type == Conversation.Type.ARCHIVED_FOOTER) {
      count = CursorUtil.requireInt(cursor, ARCHIVED_COLUMNS[1]);
    }

    boolean showTip = false;
    if (type == Conversation.Type.CONVERSATION_FILTER_FOOTER) {
      showTip = CursorUtil.requireBoolean(cursor, FILTER_FOOTER_COLUMNS[1]);
    }

    return buildThreadRecordForType(type, count, showTip);
  }

  public static ThreadRecord buildThreadRecordForType(@NonNull Conversation.Type type, int count, boolean showTip) {
    return new ThreadRecord.Builder(-(100 + type.ordinal()))
        .setBody(type.toString())
        .setDate(100)
        .setRecipient(Recipient.UNKNOWN)
        .setUnreadCount(count)
        .setType(showTip ? TYPE_SHOW_TIP : TYPE_NONE)
        .build();
  }
}
