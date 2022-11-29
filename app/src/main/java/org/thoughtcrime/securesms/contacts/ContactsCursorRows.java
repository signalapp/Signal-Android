package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.util.OptionalUtil;

/**
 * Helper utility for generating cursors and cursor rows for subclasses of {@link AbstractContactsCursorLoader}.
 */
public final class ContactsCursorRows {

  private static final String[] CONTACT_PROJECTION = new String[]{ContactRepository.ID_COLUMN,
                                                                  ContactRepository.NAME_COLUMN,
                                                                  ContactRepository.NUMBER_COLUMN,
                                                                  ContactRepository.NUMBER_TYPE_COLUMN,
                                                                  ContactRepository.LABEL_COLUMN,
                                                                  ContactRepository.CONTACT_TYPE_COLUMN,
                                                                  ContactRepository.ABOUT_COLUMN};

  /**
   * Create a {@link MatrixCursor} with the proper projection for a subclass of {@link AbstractContactsCursorLoader}
   */
  public static @NonNull MatrixCursor createMatrixCursor() {
    return new MatrixCursor(CONTACT_PROJECTION);
  }

  /**
   * Create a {@link MatrixCursor} with the proper projection for a subclass of {@link AbstractContactsCursorLoader}
   *
   * @param initialCapacity The initial capacity to hand to the {@link MatrixCursor}
   */
  public static @NonNull MatrixCursor createMatrixCursor(int initialCapacity) {
    return new MatrixCursor(CONTACT_PROJECTION, initialCapacity);
  }

  /**
   * Create a row for a contacts cursor based off the given recipient.
   */
  public static @NonNull Object[] forRecipient(@NonNull Context context, @NonNull Recipient recipient) {
    String stringId = recipient.isGroup() ? recipient.requireGroupId().toString()
                                          : OptionalUtil.or(recipient.getE164().map(PhoneNumberFormatter::prettyPrint), recipient.getEmail()).orElse("");

    return new Object[]{recipient.getId().serialize(),
                        recipient.getDisplayName(context),
                        stringId,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                        "",
                        ContactRepository.RECENT_TYPE | (recipient.isRegistered() && !recipient.isForceSmsSelection() ? ContactRepository.PUSH_TYPE : 0),
                        recipient.getCombinedAboutAndEmoji()};
  }

  /**
   * Create a row for a contacts cursor based off the given system contact.
   */
  public static @NonNull Object[] forNonPushContact(@NonNull Cursor systemContactCursor) {
    return new Object[]{systemContactCursor.getLong(systemContactCursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN)),
                        systemContactCursor.getString(systemContactCursor.getColumnIndexOrThrow(ContactRepository.NAME_COLUMN)),
                        systemContactCursor.getString(systemContactCursor.getColumnIndexOrThrow(ContactRepository.NUMBER_COLUMN)),
                        systemContactCursor.getString(systemContactCursor.getColumnIndexOrThrow(ContactRepository.NUMBER_TYPE_COLUMN)),
                        systemContactCursor.getString(systemContactCursor.getColumnIndexOrThrow(ContactRepository.LABEL_COLUMN)),
                        ContactRepository.NORMAL_TYPE,
                        ""};
  }

  /**
   * Create a row for a contacts cursor based off the given group record.
   */
  public static @NonNull Object[] forGroup(@NonNull GroupTable.GroupRecord groupRecord) {
    return new Object[]{groupRecord.getRecipientId().serialize(),
                        groupRecord.getTitle(),
                        groupRecord.getId(),
                        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                        "",
                        ContactRepository.NORMAL_TYPE,
                        ""};
  }

  /**
   * Create a row for a contacts cursor for a new number the user is entering or has entered.
   */
  public static @NonNull MatrixCursor forNewNumber(@NonNull String unknownContactTitle, @NonNull String filter) {
    MatrixCursor matrixCursor = createMatrixCursor(1);

    matrixCursor.addRow(new Object[]{null,
                                     unknownContactTitle,
                                     filter,
                                     ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                     "\u21e2",
                                     ContactRepository.NEW_PHONE_TYPE,
                                     ""});

    return matrixCursor;
  }

  /**
   * Create a row for a contacts cursor for a username the user is entering or has entered.
   */
  public static @NonNull MatrixCursor forUsernameSearch(@NonNull String filter) {
    MatrixCursor matrixCursor = createMatrixCursor(1);

    matrixCursor.addRow(new Object[]{null,
                                     null,
                                     filter,
                                     ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                     "\u21e2",
                                     ContactRepository.NEW_USERNAME_TYPE,
                                     ""});

    return matrixCursor;
  }

  public static @NonNull MatrixCursor forUsernameSearchHeader(@NonNull Context context) {
    return forHeader(context.getString(R.string.ContactsCursorLoader_find_by_username));
  }

  public static @NonNull MatrixCursor forPhoneNumberSearchHeader(@NonNull Context context) {
    return forHeader(context.getString(R.string.ContactsCursorLoader_phone_number_search));
  }

  public static @NonNull MatrixCursor forGroupsHeader(@NonNull Context context) {
    return forHeader(context.getString(R.string.ContactsCursorLoader_groups));
  }

  public static @NonNull MatrixCursor forRecentsHeader(@NonNull Context context) {
    return forHeader(context.getString(R.string.ContactsCursorLoader_recent_chats));
  }

  public static @NonNull MatrixCursor forContactsHeader(@NonNull Context context) {
    return forHeader(context.getString(R.string.ContactsCursorLoader_contacts));
  }

  public static @NonNull MatrixCursor forHeader(@NonNull String name) {
    MatrixCursor matrixCursor = createMatrixCursor(1);

    matrixCursor.addRow(new Object[]{null,
                                     name,
                                     "",
                                     ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                                     "",
                                     ContactRepository.DIVIDER_TYPE,
                                     ""});

    return matrixCursor;
  }
}
