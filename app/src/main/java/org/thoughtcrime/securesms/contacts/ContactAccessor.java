/**
 * Copyright (C) 2011 Whisper Systems
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.util.SqlUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This class was originally a layer of indirection between
 * ContactAccessorNewApi and ContactAccessorOldApi, which corresponded
 * to the API changes between 1.x and 2.x.
 *
 * Now that we no longer support 1.x, this class mostly serves as a place
 * to encapsulate Contact-related logic.  It's still a singleton, mostly
 * just because that's how it's currently called from everywhere.
 *
 * @author Moxie Marlinspike
 */

public class ContactAccessor {

  public static final String PUSH_COLUMN = "push";

  private static final String GIVEN_NAME  = ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME;
  private static final String FAMILY_NAME = ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME;

  private static final ContactAccessor instance = new ContactAccessor();

  public static synchronized ContactAccessor getInstance() {
    return instance;
  }

  public Set<String> getAllContactsWithNumbers(Context context) {
    Set<String> results = new HashSet<>();

    try (Cursor cursor = context.getContentResolver().query(Phone.CONTENT_URI, new String[] {Phone.NUMBER}, null ,null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        if (!TextUtils.isEmpty(cursor.getString(0))) {
          results.add(PhoneNumberFormatter.get(context).format(cursor.getString(0)));
        }
      }
    }

    return results;
  }

  /**
   * Gets and returns a cursor of data for all contacts, containing both phone number data and
   * structured name data.
   *
   * Cursor rows are ordered as follows:
   *
   * <ol>
   *   <li>Contact Lookup Key</li>
   *   <li>Mimetype</li>
   *   <li>id</li>
   * </ol>
   *
   * The lookup key is a fixed value that allows you to verify two rows in the database actually
   * belong to the same contact, since the contact uri can be unstable (if a sync fails, say.)
   *
   * We order by id explicitly here for the same contact sync failure error, which could result in
   * multiple structured name rows for the same user. By ordering by id DESC, we ensure we get
   * whatever the latest input data was.
   *
   * What this results in is a cursor that looks like:
   *
   * Alice phone 1
   * Alice phone 2
   * Alice structured name 2
   * Alice structured name 1
   * Bob phone 1
   * ... etc.
   */
  public Cursor getAllSystemContacts(Context context) {
    Uri      uri        = ContactsContract.Data.CONTENT_URI;
    String[] projection = SqlUtil.buildArgs(ContactsContract.Data.MIMETYPE, Phone.NUMBER, Phone.DISPLAY_NAME, Phone.LABEL, Phone.PHOTO_URI, Phone._ID, Phone.LOOKUP_KEY, Phone.TYPE, GIVEN_NAME, FAMILY_NAME);
    String   where      = ContactsContract.Data.MIMETYPE + " IN (?, ?)";
    String[] args       = SqlUtil.buildArgs(Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
    String   orderBy    = Phone.LOOKUP_KEY + " ASC, " + ContactsContract.Data.MIMETYPE + " DESC, " + ContactsContract.CommonDataKinds.Phone._ID + " DESC";

    return context.getContentResolver().query(uri, projection, where, args, orderBy);
  }

  public String getNameFromContact(Context context, Uri uri) {
    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[] {Contacts.DISPLAY_NAME},
                                                  null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getString(0);

    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  public ContactData getContactData(Context context, Uri uri) {
    return getContactData(context, getNameFromContact(context, uri),  Long.parseLong(uri.getLastPathSegment()));
  }

  private ContactData getContactData(Context context, String displayName, long id) {
    ContactData contactData = new ContactData(id, displayName);

    try (Cursor numberCursor = context.getContentResolver().query(Phone.CONTENT_URI,
                                                                  null,
                                                                  Phone.CONTACT_ID + " = ?",
                                                                  new String[] {contactData.id + ""},
                                                                  null))
    {
      while (numberCursor != null && numberCursor.moveToNext()) {
        int type         = numberCursor.getInt(numberCursor.getColumnIndexOrThrow(Phone.TYPE));
        String label     = numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.LABEL));
        String number    = numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.NUMBER));
        String typeLabel = Phone.getTypeLabel(context.getResources(), type, label).toString();

        contactData.numbers.add(new NumberData(typeLabel, number));
      }
    }

    return contactData;
  }

  public CharSequence phoneTypeToString(Context mContext, int type, CharSequence label) {
    return Phone.getTypeLabel(mContext.getResources(), type, label);
  }

  public static class NumberData implements Parcelable {

    public static final Parcelable.Creator<NumberData> CREATOR = new Parcelable.Creator<NumberData>() {
      public NumberData createFromParcel(Parcel in) {
        return new NumberData(in);
      }

      public NumberData[] newArray(int size) {
        return new NumberData[size];
      }
    };

    public final String number;
    public final String type;

    public NumberData(String type, String number) {
      this.type = type;
      this.number = number;
    }

    public NumberData(Parcel in) {
      number = in.readString();
      type   = in.readString();
    }

    public int describeContents() {
      return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(number);
      dest.writeString(type);
    }
  }

  public static class ContactData implements Parcelable {

    public static final Parcelable.Creator<ContactData> CREATOR = new Parcelable.Creator<ContactData>() {
      public ContactData createFromParcel(Parcel in) {
        return new ContactData(in);
      }

      public ContactData[] newArray(int size) {
        return new ContactData[size];
      }
    };

    public final long id;
    public final String name;
    public final List<NumberData> numbers;

    public ContactData(long id, String name) {
      this.id      = id;
      this.name    = name;
      this.numbers = new LinkedList<NumberData>();
    }

    public ContactData(Parcel in) {
      id      = in.readLong();
      name    = in.readString();
      numbers = new LinkedList<NumberData>();
      in.readTypedList(numbers, NumberData.CREATOR);
    }

    public int describeContents() {
      return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(id);
      dest.writeString(name);
      dest.writeTypedList(numbers);
    }
  }

  /***
   * If the code below looks shitty to you, that's because it was taken
   * directly from the Android source, where shitty code is all you get.
   */

  public Cursor getCursorForRecipientFilter(CharSequence constraint,
      ContentResolver mContentResolver)
  {
    final String SORT_ORDER = Contacts.TIMES_CONTACTED + " DESC," +
                              Contacts.DISPLAY_NAME + "," +
                              Contacts.Data.IS_SUPER_PRIMARY + " DESC," +
                              Phone.TYPE;

    final String[] PROJECTION_PHONE = {
        Phone._ID,                  // 0
        Phone.CONTACT_ID,           // 1
        Phone.TYPE,                 // 2
        Phone.NUMBER,               // 3
        Phone.LABEL,                // 4
        Phone.DISPLAY_NAME,         // 5
    };

    String phone = "";
    String cons  = null;

    if (constraint != null) {
      cons = constraint.toString();

      if (RecipientsAdapter.usefulAsDigits(cons)) {
        phone = PhoneNumberUtils.convertKeypadLettersToDigits(cons);
        if (phone.equals(cons) && !PhoneNumberUtils.isWellFormedSmsAddress(phone)) {
          phone = "";
        } else {
          phone = phone.trim();
        }
      }
    }
    Uri uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(cons));
    String selection = String.format("%s=%s OR %s=%s OR %s=%s",
                                     Phone.TYPE,
                                     Phone.TYPE_MOBILE,
                                     Phone.TYPE,
                                     Phone.TYPE_WORK_MOBILE,
                                     Phone.TYPE,
                                     Phone.TYPE_MMS);

    Cursor phoneCursor = mContentResolver.query(uri,
                                                PROJECTION_PHONE,
                                                null,
                                                null,
                                                SORT_ORDER);

    if (phone.length() > 0) {
      ArrayList result = new ArrayList();
      result.add(Integer.valueOf(-1));                    // ID
      result.add(Long.valueOf(-1));                       // CONTACT_ID
      result.add(Integer.valueOf(Phone.TYPE_CUSTOM));     // TYPE
      result.add(phone);                                  // NUMBER

    /*
    * The "\u00A0" keeps Phone.getDisplayLabel() from deciding
    * to display the default label ("Home") next to the transformation
    * of the letters into numbers.
    */
      result.add("\u00A0");                               // LABEL
      result.add(cons);                                   // NAME

      ArrayList<ArrayList> wrap = new ArrayList<ArrayList>();
      wrap.add(result);

      ArrayListCursor translated = new ArrayListCursor(PROJECTION_PHONE, wrap);

      return new MergeCursor(new Cursor[] { translated, phoneCursor });
    } else {
      return phoneCursor;
    }
  }

}
