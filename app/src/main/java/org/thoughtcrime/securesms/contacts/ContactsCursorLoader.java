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

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.CursorLoader;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.UsernameUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();

  public static final class DisplayMode {
    public static final int FLAG_PUSH            = 1;
    public static final int FLAG_SMS             = 1 << 1;
    public static final int FLAG_ACTIVE_GROUPS   = 1 << 2;
    public static final int FLAG_INACTIVE_GROUPS = 1 << 3;
    public static final int FLAG_ALL             = FLAG_PUSH |  FLAG_SMS | FLAG_ACTIVE_GROUPS | FLAG_INACTIVE_GROUPS;
  }

  private static final String[] CONTACT_PROJECTION = new String[]{ContactRepository.ID_COLUMN,
                                                                  ContactRepository.NAME_COLUMN,
                                                                  ContactRepository.NUMBER_COLUMN,
                                                                  ContactRepository.NUMBER_TYPE_COLUMN,
                                                                  ContactRepository.LABEL_COLUMN,
                                                                  ContactRepository.CONTACT_TYPE_COLUMN};

  private static final int RECENT_CONVERSATION_MAX = 25;

  private final String  filter;
  private final int     mode;
  private final boolean recents;

  private final ContactRepository contactRepository;

  public ContactsCursorLoader(@NonNull Context context, int mode, String filter, boolean recents)
  {
    super(context);

    if (flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS) && !flagSet(mode, DisplayMode.FLAG_ACTIVE_GROUPS)) {
      throw new AssertionError("Inactive group flag set, but the active group flag isn't!");
    }

    this.filter            = sanitizeFilter(filter);
    this.mode              = mode;
    this.recents           = recents;
    this.contactRepository = new ContactRepository(context);
  }

  @Override
  public Cursor loadInBackground() {
    List<Cursor> cursorList = TextUtils.isEmpty(filter) ? getUnfilteredResults()
                                                        : getFilteredResults();
    if (cursorList.size() > 0) {
      return new MergeCursor(cursorList.toArray(new Cursor[0]));
    }
    return null;
  }

  private static @NonNull String sanitizeFilter(@Nullable String filter) {
    if (filter == null) {
      return "";
    } else if (filter.startsWith("@")) {
      return filter.substring(1);
    } else {
      return filter;
    }
  }

  private List<Cursor> getUnfilteredResults() {
    ArrayList<Cursor> cursorList = new ArrayList<>();

    if (recents) {
      Cursor recentConversations = getRecentConversationsCursor();
      if (recentConversations.getCount() > 0) {
        cursorList.add(getRecentsHeaderCursor());
        cursorList.add(recentConversations);
        cursorList.add(getContactsHeaderCursor());
      }
    }
    cursorList.addAll(getContactsCursors());
    return cursorList;
  }

  private List<Cursor> getFilteredResults() {
    ArrayList<Cursor> cursorList = new ArrayList<>();

    if (groupsEnabled(mode)) {
      Cursor groups = getGroupsCursor();
      if (groups.getCount() > 0) {
        List<Cursor> contacts = getContactsCursors();
        if (!isCursorListEmpty(contacts)) {
          cursorList.add(getContactsHeaderCursor());
          cursorList.addAll(contacts);
          cursorList.add(getGroupsHeaderCursor());
        }
        cursorList.add(groups);
      } else {
        cursorList.addAll(getContactsCursors());
      }
    } else {
      cursorList.addAll(getContactsCursors());
    }

    if (FeatureFlags.usernames() && NumberUtil.isVisuallyValidNumberOrEmail(filter)) {
      cursorList.add(getPhoneNumberSearchHeaderCursor());
      cursorList.add(getNewNumberCursor());
    } else if (!FeatureFlags.usernames() && NumberUtil.isValidSmsOrEmail(filter)){
      cursorList.add(getContactsHeaderCursor());
      cursorList.add(getNewNumberCursor());
    }

    if (FeatureFlags.usernames() && UsernameUtil.isValidUsernameForSearch(filter)) {
      cursorList.add(getUsernameSearchHeaderCursor());
      cursorList.add(getUsernameSearchCursor());
    }

    return cursorList;
  }

  private Cursor getRecentsHeaderCursor() {
    MatrixCursor recentsHeader = new MatrixCursor(CONTACT_PROJECTION);
    recentsHeader.addRow(new Object[]{ null,
                                       getContext().getString(R.string.ContactsCursorLoader_recent_chats),
                                       "",
                                       ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                       "",
                                       ContactRepository.DIVIDER_TYPE });
    return recentsHeader;
  }

  private Cursor getContactsHeaderCursor() {
    MatrixCursor contactsHeader = new MatrixCursor(CONTACT_PROJECTION, 1);
    contactsHeader.addRow(new Object[] { null,
                                         getContext().getString(R.string.ContactsCursorLoader_contacts),
                                         "",
                                         ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                         "",
                                         ContactRepository.DIVIDER_TYPE });
    return contactsHeader;
  }

  private Cursor getGroupsHeaderCursor() {
    MatrixCursor groupHeader = new MatrixCursor(CONTACT_PROJECTION, 1);
    groupHeader.addRow(new Object[]{ null,
                                     getContext().getString(R.string.ContactsCursorLoader_groups),
                                     "",
                                     ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                     "",
                                     ContactRepository.DIVIDER_TYPE });
    return groupHeader;
  }

  private Cursor getPhoneNumberSearchHeaderCursor() {
    MatrixCursor contactsHeader = new MatrixCursor(CONTACT_PROJECTION, 1);
    contactsHeader.addRow(new Object[] { null,
                                         getContext().getString(R.string.ContactsCursorLoader_phone_number_search),
                                         "",
                                         ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                         "",
                                         ContactRepository.DIVIDER_TYPE });
    return contactsHeader;
  }

  private Cursor getUsernameSearchHeaderCursor() {
    MatrixCursor contactsHeader = new MatrixCursor(CONTACT_PROJECTION, 1);
    contactsHeader.addRow(new Object[] { null,
                                         getContext().getString(R.string.ContactsCursorLoader_username_search),
                                         "",
                                         ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                         "",
                                         ContactRepository.DIVIDER_TYPE });
    return contactsHeader;
  }


  private Cursor getRecentConversationsCursor() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(getContext());

    MatrixCursor recentConversations = new MatrixCursor(CONTACT_PROJECTION, RECENT_CONVERSATION_MAX);
    try (Cursor rawConversations = threadDatabase.getRecentConversationList(RECENT_CONVERSATION_MAX, flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS))) {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(rawConversations);
      ThreadRecord threadRecord;
      while ((threadRecord = reader.getNext()) != null) {
        Recipient recipient = threadRecord.getRecipient();
        String    stringId  = recipient.isGroup() ? recipient.requireGroupId().toString() : recipient.getE164().or(recipient.getEmail()).or("");

        recentConversations.addRow(new Object[] { recipient.getId().serialize(),
                                                  recipient.toShortString(getContext()),
                                                  stringId,
                                                  ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                                  "",
                                                  ContactRepository.RECENT_TYPE });
      }
    }
    return recentConversations;
  }

  private List<Cursor> getContactsCursors() {
    List<Cursor> cursorList = new ArrayList<>(2);

    if (!Permissions.hasAny(getContext(), Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      return cursorList;
    }

    if (pushEnabled(mode)) {
      cursorList.add(contactRepository.querySignalContacts(filter));
    }

    if (pushEnabled(mode) && smsEnabled(mode)) {
      cursorList.add(contactRepository.queryNonSignalContacts(filter));
    } else if (smsEnabled(mode)) {
      cursorList.add(filterNonPushContacts(contactRepository.queryNonSignalContacts(filter)));
    }
    return cursorList;
  }

  private Cursor getGroupsCursor() {
    MatrixCursor groupContacts = new MatrixCursor(CONTACT_PROJECTION);
    try (GroupDatabase.Reader reader = DatabaseFactory.getGroupDatabase(getContext()).getGroupsFilteredByTitle(filter, flagSet(mode, DisplayMode.FLAG_INACTIVE_GROUPS))) {
      GroupDatabase.GroupRecord groupRecord;
      while ((groupRecord = reader.getNext()) != null) {
        groupContacts.addRow(new Object[] { groupRecord.getRecipientId().serialize(),
                                            groupRecord.getTitle(),
                                            groupRecord.getId(),
                                            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                            "",
                                            ContactRepository.NORMAL_TYPE });
      }
    }
    return groupContacts;
  }

  private Cursor getNewNumberCursor() {
    MatrixCursor newNumberCursor = new MatrixCursor(CONTACT_PROJECTION, 1);
    newNumberCursor.addRow(new Object[] { null,
                                          getContext().getString(R.string.contact_selection_list__unknown_contact),
                                          filter,
                                          ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                          "\u21e2",
                                          ContactRepository.NEW_PHONE_TYPE});
    return newNumberCursor;
  }

  private Cursor getUsernameSearchCursor() {
    MatrixCursor cursor = new MatrixCursor(CONTACT_PROJECTION, 1);
    cursor.addRow(new Object[] { null,
                                 getContext().getString(R.string.contact_selection_list__unknown_contact),
                                 filter,
                                 ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                 "\u21e2",
                                 ContactRepository.NEW_USERNAME_TYPE});
    return cursor;
  }

  private @NonNull Cursor filterNonPushContacts(@NonNull Cursor cursor) {
    try {
      final long startMillis = System.currentTimeMillis();
      final MatrixCursor matrix = new MatrixCursor(CONTACT_PROJECTION);
      while (cursor.moveToNext()) {
        final RecipientId id        = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN)));
        final Recipient   recipient = Recipient.resolved(id);

        if (recipient.resolve().getRegistered() != RecipientDatabase.RegisteredState.REGISTERED) {
          matrix.addRow(new Object[]{cursor.getLong(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NAME_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NUMBER_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NUMBER_TYPE_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.LABEL_COLUMN)),
                                     ContactRepository.NORMAL_TYPE});
        }
      }
      Log.i(TAG, "filterNonPushContacts() -> " + (System.currentTimeMillis() - startMillis) + "ms");
      return matrix;
    } finally {
      cursor.close();
    }
  }

  private static boolean isCursorListEmpty(List<Cursor> list) {
    int sum = 0;
    for (Cursor cursor : list) {
      sum += cursor.getCount();
    }
    return sum == 0;
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

  private static boolean flagSet(int mode, int flag) {
    return (mode & flag) > 0;
  }
}
