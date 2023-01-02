/*
 * Copyright (C) 2013-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;

import androidx.annotation.NonNull;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends AbstractContactsCursorLoader {

  private static final String TAG = Log.tag(ContactsCursorLoader.class);

  public static final class DisplayMode {
    public static final int FLAG_PUSH                  = 1;
    public static final int FLAG_SMS                   = 1 << 1;
    public static final int FLAG_ACTIVE_GROUPS         = 1 << 2;
    public static final int FLAG_INACTIVE_GROUPS       = 1 << 3;
    public static final int FLAG_SELF                  = 1 << 4;
    public static final int FLAG_BLOCK                 = 1 << 5;
    public static final int FLAG_HIDE_GROUPS_V1        = 1 << 5;
    public static final int FLAG_HIDE_NEW              = 1 << 6;
    public static final int FLAG_HIDE_RECENT_HEADER    = 1 << 7;
    public static final int FLAG_GROUPS_AFTER_CONTACTS = 1 << 8;
    public static final int FLAG_ALL                   = FLAG_PUSH | FLAG_SMS | FLAG_ACTIVE_GROUPS | FLAG_INACTIVE_GROUPS | FLAG_SELF;
  }

  private static final int RECENT_CONVERSATION_MAX = 25;

  private final int              mode;
  private final boolean          recents;

  private final ContactRepository contactRepository;

  private ContactsCursorLoader(@NonNull Context context, int mode, String filter, boolean recents)
  {
    super(context, filter);

    if (flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS) && !flagSet(mode, DisplayMode.FLAG_ACTIVE_GROUPS)) {
      throw new AssertionError("Inactive group flag set, but the active group flag isn't!");
    }

    this.mode              = mode;
    this.recents           = recents;
    this.contactRepository = new ContactRepository(context, context.getString(R.string.note_to_self));
  }

  protected final List<Cursor> getUnfilteredResults() {
    ArrayList<Cursor> cursorList = new ArrayList<>();

    if (groupsOnly(mode)) {
      addRecentGroupsSection(cursorList);
      addGroupsSection(cursorList);
    } else {
      addRecentsSection(cursorList);
      addContactsSection(cursorList);
      if (addGroupsAfterContacts(mode)) {
        addGroupsSection(cursorList);
      }
    }

    return cursorList;
  }

  protected final List<Cursor> getFilteredResults() {
    ArrayList<Cursor> cursorList = new ArrayList<>();

    addContactsSection(cursorList);
    addGroupsSection(cursorList);

    if (!hideNewNumberOrUsername(mode)) {
      addNewNumberSection(cursorList);
      addUsernameSearchSection(cursorList);
    }

    return cursorList;
  }

  private void addRecentsSection(@NonNull List<Cursor> cursorList) {
    if (!recents) {
      return;
    }

    Cursor recentConversations = getRecentConversationsCursor();

    if (recentConversations.getCount() > 0) {
      if (!hideRecentsHeader(mode)) {
        cursorList.add(ContactsCursorRows.forRecentsHeader(getContext()));
      }
      cursorList.add(recentConversations);
    }
  }

  private void addContactsSection(@NonNull List<Cursor> cursorList) {
    List<Cursor> contacts = getContactsCursors();

    if (!isCursorListEmpty(contacts)) {
      if (!getFilter().isEmpty() || recents) {
        cursorList.add(ContactsCursorRows.forContactsHeader(getContext()));
      }
      cursorList.addAll(contacts);
    }
  }

  private void addRecentGroupsSection(@NonNull List<Cursor> cursorList) {
    if (!groupsEnabled(mode) || !recents) {
      return;
    }

    Cursor groups = getRecentConversationsCursor(true);

    if (groups.getCount() > 0) {
      if (!hideRecentsHeader(mode)) {
        cursorList.add(ContactsCursorRows.forRecentsHeader(getContext()));
      }
      cursorList.add(groups);
    }
  }

  private void addGroupsSection(@NonNull List<Cursor> cursorList) {
    if (!groupsEnabled(mode)) {
      return;
    }

    Cursor groups = getGroupsCursor();

    if (groups.getCount() > 0) {
      cursorList.add(ContactsCursorRows.forGroupsHeader(getContext()));
      cursorList.add(groups);
    }
  }

  private void addNewNumberSection(@NonNull List<Cursor> cursorList) {
    if (FeatureFlags.usernames() && NumberUtil.isVisuallyValidNumberOrEmail(getFilter())) {
      cursorList.add(ContactsCursorRows.forPhoneNumberSearchHeader(getContext()));
      cursorList.add(getNewNumberCursor());
    } else if (!FeatureFlags.usernames() && NumberUtil.isValidSmsOrEmail(getFilter())) {
      cursorList.add(ContactsCursorRows.forPhoneNumberSearchHeader(getContext()));
      cursorList.add(getNewNumberCursor());
    }
  }

  private void addUsernameSearchSection(@NonNull List<Cursor> cursorList) {
    if (FeatureFlags.usernames() && UsernameUtil.isValidUsernameForSearch(getFilter())) {
      cursorList.add(ContactsCursorRows.forUsernameSearchHeader(getContext()));
      cursorList.add(getUsernameSearchCursor());
    }
  }

  private Cursor getRecentConversationsCursor() {
    return getRecentConversationsCursor(false);
  }

  private Cursor getRecentConversationsCursor(boolean groupsOnly) {
    ThreadTable threadTable = SignalDatabase.threads();

    MatrixCursor recentConversations = ContactsCursorRows.createMatrixCursor(RECENT_CONVERSATION_MAX);
    try (Cursor rawConversations = threadTable.getRecentConversationList(RECENT_CONVERSATION_MAX, flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS), false, groupsOnly, hideGroupsV1(mode), !smsEnabled(mode), false)) {
      ThreadTable.Reader reader = threadTable.readerFor(rawConversations);
      ThreadRecord       threadRecord;
      while ((threadRecord = reader.getNext()) != null) {
        recentConversations.addRow(ContactsCursorRows.forRecipient(getContext(), threadRecord.getRecipient()));
      }
    }
    return recentConversations;
  }

  private List<Cursor> getContactsCursors() {
    List<Cursor> cursorList = new ArrayList<>(2);

    if (pushEnabled(mode) && smsEnabled(mode)) {
      cursorList.add(contactRepository.queryNonGroupContacts(getFilter(), selfEnabled(mode)));
    } else if (pushEnabled(mode)) {
      cursorList.add(contactRepository.querySignalContacts(getFilter(), selfEnabled(mode)));
    } else if (smsEnabled(mode)) {
      cursorList.add(contactRepository.queryNonSignalContacts(getFilter()));
    }

    return cursorList;
  }

  private Cursor getGroupsCursor() {
    MatrixCursor                  groupContacts = ContactsCursorRows.createMatrixCursor();
    Map<RecipientId, GroupRecord> groups        = new LinkedHashMap<>();

    try (GroupTable.Reader reader = SignalDatabase.groups().queryGroupsByTitle(getFilter(), flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS), hideGroupsV1(mode), !smsEnabled(mode))) {
      GroupRecord groupRecord;
      while ((groupRecord = reader.getNext()) != null) {
        groups.put(groupRecord.getRecipientId(), groupRecord);
      }
    }

    if (getFilter() != null && !Util.isEmpty(getFilter())) {
      Set<RecipientId> filteredContacts = new HashSet<>();
      try (Cursor cursor = SignalDatabase.recipients().queryAllContacts(getFilter())) {
        while (cursor != null && cursor.moveToNext()) {
          filteredContacts.add(RecipientId.from(CursorUtil.requireString(cursor, RecipientTable.ID)));
        }
      }

      try (GroupTable.Reader reader = SignalDatabase.groups().queryGroupsByMembership(filteredContacts, flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS), hideGroupsV1(mode), !smsEnabled(mode))) {
        GroupRecord groupRecord;
        while ((groupRecord = reader.getNext()) != null) {
          groups.put(groupRecord.getRecipientId(), groupRecord);
        }
      }
    }

    for (GroupRecord groupRecord : groups.values()) {
      groupContacts.addRow(ContactsCursorRows.forGroup(groupRecord));
    }

    return groupContacts;
  }

  private Cursor getNewNumberCursor() {
    return ContactsCursorRows.forNewNumber(getUnknownContactTitle(), getFilter());
  }

  private Cursor getUsernameSearchCursor() {
    return ContactsCursorRows.forUsernameSearch(getFilter());
  }

  private String getUnknownContactTitle() {
    if (blockUser(mode)) {
      return getContext().getString(R.string.contact_selection_list__unknown_contact_block);
    } else if (newConversation(mode)) {
      return getContext().getString(R.string.contact_selection_list__unknown_contact);
    } else {
      return getContext().getString(R.string.contact_selection_list__unknown_contact_add_to_group);
    }
  }

  private static boolean isCursorListEmpty(List<Cursor> list) {
    int sum = 0;
    for (Cursor cursor : list) {
      sum += cursor.getCount();
    }
    return sum == 0;
  }

  private static boolean selfEnabled(int mode) {
    return flagSet(mode, DisplayMode.FLAG_SELF);
  }

  private static boolean blockUser(int mode) {
    return flagSet(mode, DisplayMode.FLAG_BLOCK);
  }

  private static boolean newConversation(int mode) {
    return groupsEnabled(mode);
  }

  private static boolean pushEnabled(int mode) {
    return flagSet(mode, DisplayMode.FLAG_PUSH);
  }

  private static boolean smsEnabled(int mode) {
    return flagSet(mode, DisplayMode.FLAG_SMS);
  }

  private static boolean groupsEnabled(int mode) {
    return flagSet(mode, DisplayMode.FLAG_ACTIVE_GROUPS);
  }

  private static boolean groupsOnly(int mode) {
    return mode == DisplayMode.FLAG_ACTIVE_GROUPS;
  }

  private static boolean hideGroupsV1(int mode) {
    return flagSet(mode, DisplayMode.FLAG_HIDE_GROUPS_V1);
  }

  private static boolean hideNewNumberOrUsername(int mode) {
    return flagSet(mode, DisplayMode.FLAG_HIDE_NEW);
  }

  private static boolean hideRecentsHeader(int mode) {
    return flagSet(mode, DisplayMode.FLAG_HIDE_RECENT_HEADER);
  }

  private static boolean addGroupsAfterContacts(int mode) {
    return flagSet(mode, DisplayMode.FLAG_GROUPS_AFTER_CONTACTS);
  }

  private static boolean flagSet(int mode, int flag) {
    return (mode & flag) > 0;
  }

  public static class Factory implements AbstractContactsCursorLoader.Factory {

    private final Context          context;
    private final int              displayMode;
    private final String           cursorFilter;
    private final boolean          displayRecents;

    public Factory(Context context, int displayMode, String cursorFilter, boolean displayRecents) {
      this.context        = context;
      this.displayMode    = displayMode;
      this.cursorFilter   = cursorFilter;
      this.displayRecents = displayRecents;
    }

    @Override
    public @NonNull AbstractContactsCursorLoader create() {
      return new ContactsCursorLoader(context, displayMode, cursorFilter, displayRecents);
    }
  }
}
