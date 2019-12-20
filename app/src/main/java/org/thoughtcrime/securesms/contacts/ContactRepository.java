package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for all contacts. Allows you to filter them via queries.
 *
 * Currently this is implemented to return cursors. This is to ease the migration between this class
 * and the previous way we'd query contacts: {@link ContactsDatabase}. It's much easier in the
 * short-term to mock the cursor interface rather than try to switch everything over to models.
 */
public class ContactRepository {

  private final RecipientDatabase recipientDatabase;
  private final String            noteToSelfTitle;
  private final Context           context;

  public static final String ID_COLUMN           = "id";
         static final String NAME_COLUMN         = "name";
         static final String NUMBER_COLUMN       = "number";
         static final String NUMBER_TYPE_COLUMN  = "number_type";
         static final String LABEL_COLUMN        = "label";
         static final String CONTACT_TYPE_COLUMN = "contact_type";

  static final int NORMAL_TYPE       = 0;
  static final int PUSH_TYPE         = 1;
  static final int NEW_PHONE_TYPE    = 2;
  static final int NEW_USERNAME_TYPE = 3;
  static final int RECENT_TYPE       = 4;
  static final int DIVIDER_TYPE      = 5;

  /** Maps the recipient results to the legacy contact column names */
  private static final List<Pair<String, ValueMapper>> SEARCH_CURSOR_MAPPERS = new ArrayList<Pair<String, ValueMapper>>() {{
    add(new Pair<>(ID_COLUMN, cursor -> cursor.getLong(cursor.getColumnIndexOrThrow(RecipientDatabase.ID))));

    add(new Pair<>(NAME_COLUMN, cursor -> {
      String system  = cursor.getString(cursor.getColumnIndexOrThrow(RecipientDatabase.SYSTEM_DISPLAY_NAME));
      String profile = cursor.getString(cursor.getColumnIndexOrThrow(RecipientDatabase.SEARCH_PROFILE_NAME));

      return Util.getFirstNonEmpty(system, profile);
    }));

    add(new Pair<>(NUMBER_COLUMN, cursor -> {
      String phone = cursor.getString(cursor.getColumnIndexOrThrow(RecipientDatabase.PHONE));
      String email = cursor.getString(cursor.getColumnIndexOrThrow(RecipientDatabase.EMAIL));

      return Util.getFirstNonEmpty(phone, email);
    }));

    add(new Pair<>(NUMBER_TYPE_COLUMN, cursor -> cursor.getInt(cursor.getColumnIndexOrThrow(RecipientDatabase.SYSTEM_PHONE_TYPE))));

    add(new Pair<>(LABEL_COLUMN, cursor -> cursor.getString(cursor.getColumnIndexOrThrow(RecipientDatabase.SYSTEM_PHONE_LABEL))));

    add(new Pair<>(CONTACT_TYPE_COLUMN, cursor -> {
      int registered = cursor.getInt(cursor.getColumnIndexOrThrow(RecipientDatabase.REGISTERED));
      return registered == RecipientDatabase.RegisteredState.REGISTERED.getId() ? PUSH_TYPE : NORMAL_TYPE;
    }));
  }};

  public ContactRepository(@NonNull Context context) {
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    this.noteToSelfTitle   = context.getString(R.string.note_to_self);
    this.context           = context.getApplicationContext();
  }

  @WorkerThread
  public Cursor querySignalContacts(@NonNull String query) {
    Cursor cursor = TextUtils.isEmpty(query) ? recipientDatabase.getSignalContacts()
                                             : recipientDatabase.querySignalContacts(query);


    if (noteToSelfTitle.toLowerCase().contains(query.toLowerCase())) {
      Recipient self        = Recipient.self();
      boolean   nameMatch   = self.getDisplayName(context).toLowerCase().contains(query.toLowerCase());
      boolean   numberMatch = self.getE164().isPresent() && self.requireE164().contains(query);
      boolean   shouldAdd   = !nameMatch && !numberMatch;

      if (shouldAdd) {
        MatrixCursor selfCursor = new MatrixCursor(RecipientDatabase.SEARCH_PROJECTION_NAMES);
        selfCursor.addRow(new Object[]{ self.getId().serialize(), noteToSelfTitle, null, self.getE164().or(""), self.getEmail().orNull(), null, -1, RecipientDatabase.RegisteredState.REGISTERED.getId(), noteToSelfTitle });

        cursor = cursor == null ? selfCursor : new MergeCursor(new Cursor[]{ cursor, selfCursor });
      }
    }

    return new SearchCursorWrapper(cursor, SEARCH_CURSOR_MAPPERS);
  }

  @WorkerThread
  public Cursor queryNonSignalContacts(@NonNull String query) {
    Cursor cursor = TextUtils.isEmpty(query) ? recipientDatabase.getNonSignalContacts()
                                             : recipientDatabase.queryNonSignalContacts(query);
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
