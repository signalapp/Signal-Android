package org.thoughtcrime.securesms.contacts;

import android.database.Cursor;
import android.database.CursorWrapper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.CursorUtil;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchSortOrder;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.util.SignalE164Util;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for all contacts. Allows you to filter them via queries.
 *
 * Currently this is implemented to return cursors. This is to ease the migration between this class
 * and the previous way we'd query contacts. It's much easier in the short-term to mock the cursor
 * interface rather than try to switch everything over to models.
 */
public class ContactRepository {

  private final RecipientTable recipientTable;
  private final String         noteToSelfTitle;

  public static final String ID_COLUMN           = "id";
  public static final String NAME_COLUMN         = "name";
         static final String NUMBER_COLUMN       = "number";
         static final String NUMBER_TYPE_COLUMN  = "number_type";
         static final String LABEL_COLUMN        = "label";
         static final String CONTACT_TYPE_COLUMN = "contact_type";
         static final String ABOUT_COLUMN        = "about";

  static final int NORMAL_TYPE       = 0;
  static final int PUSH_TYPE         = 1;

  /** Maps the recipient results to the legacy contact column names */
  private static final List<Pair<String, ValueMapper>> SEARCH_CURSOR_MAPPERS = new ArrayList<Pair<String, ValueMapper>>() {{
    add(new Pair<>(ID_COLUMN, cursor -> CursorUtil.requireLong(cursor, RecipientTable.ID)));

    add(new Pair<>(NAME_COLUMN, cursor -> {
      String system  = CursorUtil.requireString(cursor, RecipientTable.SYSTEM_JOINED_NAME);
      String profile = CursorUtil.requireString(cursor, RecipientTable.SEARCH_PROFILE_NAME);

      return Util.getFirstNonEmpty(system, profile);
    }));

    add(new Pair<>(NUMBER_COLUMN, cursor -> {
      String phone = CursorUtil.requireString(cursor, RecipientTable.E164);
      String email = CursorUtil.requireString(cursor, RecipientTable.EMAIL);

      if (phone != null) {
        phone = SignalE164Util.prettyPrint(phone);
      }

      return Util.getFirstNonEmpty(phone, email);
    }));

    add(new Pair<>(NUMBER_TYPE_COLUMN, cursor -> CursorUtil.requireInt(cursor, RecipientTable.SYSTEM_PHONE_TYPE)));

    add(new Pair<>(LABEL_COLUMN, cursor -> CursorUtil.requireString(cursor, RecipientTable.SYSTEM_PHONE_LABEL)));

    add(new Pair<>(CONTACT_TYPE_COLUMN, cursor -> {
      int registered = CursorUtil.requireInt(cursor, RecipientTable.REGISTERED);
      return registered == RecipientTable.RegisteredState.REGISTERED.getId() ? PUSH_TYPE : NORMAL_TYPE;
    }));

    add(new Pair<>(ABOUT_COLUMN, cursor -> {
      String aboutEmoji = CursorUtil.requireString(cursor, RecipientTable.ABOUT_EMOJI);
      String about      = CursorUtil.requireString(cursor, RecipientTable.ABOUT);

      if (!Util.isEmpty(aboutEmoji)) {
        if (!Util.isEmpty(about)) {
          return aboutEmoji + " " + about;
        } else {
          return aboutEmoji;
        }
      } else if (!Util.isEmpty(about)) {
        return about;
      } else {
        return "";
      }
    }));
  }};

  public ContactRepository(@NonNull String noteToSelfTitle) {
    this.noteToSelfTitle = noteToSelfTitle;
    this.recipientTable  = SignalDatabase.recipients();
  }

  @WorkerThread
  public @NonNull Cursor querySignalContacts(@NonNull String query) {
    return querySignalContacts(new RecipientTable.ContactSearchQuery(query, new RecipientTable.IncludeSelfMode.IncludeWithRemap(noteToSelfTitle), ContactSearchSortOrder.NATURAL));
  }

  @WorkerThread
  public @NonNull Cursor querySignalContacts(@NonNull RecipientTable.ContactSearchQuery contactSearchQuery) {
    Cursor cursor = TextUtils.isEmpty(contactSearchQuery.getQuery()) ? recipientTable.getSignalContacts(contactSearchQuery.getIncludeSelfMode())
                                                                     : recipientTable.querySignalContacts(contactSearchQuery);

    return new SearchCursorWrapper(cursor, SEARCH_CURSOR_MAPPERS);
  }

  @WorkerThread
  public @NonNull Cursor queryGroupMemberContacts(@NonNull String query) {
    Cursor cursor = TextUtils.isEmpty(query) ? recipientTable.getGroupMemberContacts()
                                             : recipientTable.queryGroupMemberContacts(query);

    return new SearchCursorWrapper(cursor, SEARCH_CURSOR_MAPPERS);
  }

  /**
   * This lets us mock the legacy cursor interface while using the new cursor, even though the data
   * doesn't quite match up exactly.
   */
  private static class SearchCursorWrapper extends CursorWrapper {

    private final Cursor                          wrapped;
    private final String[]                        columnNames;
    private final List<Pair<String, ValueMapper>> mappers;
    private final Map<String, Integer>            positions;

    SearchCursorWrapper(Cursor cursor, @NonNull List<Pair<String, ValueMapper>> mappers) {
      super(cursor);

      this.wrapped     = cursor;
      this.mappers     = mappers;
      this.positions   = new HashMap<>();
      this.columnNames = new String[mappers.size()];

      for (int i = 0; i < mappers.size(); i++) {
        Pair<String, ValueMapper> pair = mappers.get(i);

        positions.put(pair.first(), i);
        columnNames[i] = pair.first();
      }
    }

    @Override
    public int getColumnCount() {
      return mappers.size();
    }

    @Override
    public String[] getColumnNames() {
      return columnNames;
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
      Integer index = positions.get(columnName);

      if (index != null) {
        return index;
      } else {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public String getString(int columnIndex) {
      return String.valueOf(mappers.get(columnIndex).second().get(wrapped));
    }

    @Override
    public int getInt(int columnIndex) {
      return (int) mappers.get(columnIndex).second().get(wrapped);
    }

    @Override
    public long getLong(int columnIndex) {
      return (long) mappers.get(columnIndex).second().get(wrapped);
    }
  }

  private interface ValueMapper<T> {
    T get(@NonNull Cursor cursor);
  }
}
