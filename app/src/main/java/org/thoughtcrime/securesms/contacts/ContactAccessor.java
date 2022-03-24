/**
 * Copyright (C) 2011 Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;

import java.util.LinkedList;
import java.util.List;

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

  private static final ContactAccessor instance = new ContactAccessor();

  public static ContactAccessor getInstance() {
    return instance;
  }

  public ContactData getContactData(Context context, Uri uri) {
    String displayName = getNameFromContact(context, uri);
    long   id          = Long.parseLong(uri.getLastPathSegment());

    ContactData contactData = new ContactData(id, displayName);

    try (Cursor numberCursor = context.getContentResolver().query(Phone.CONTENT_URI,
                                                                  null,
                                                                  Phone.CONTACT_ID + " = ?",
                                                                  new String[] { contactData.id + "" },
                                                                  null))
    {
      while (numberCursor != null && numberCursor.moveToNext()) {
        int    type      = numberCursor.getInt(numberCursor.getColumnIndexOrThrow(Phone.TYPE));
        String label     = numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.LABEL));
        String number    = numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.NUMBER));
        String typeLabel = Phone.getTypeLabel(context.getResources(), type, label).toString();

        contactData.numbers.add(new NumberData(typeLabel, number));
      }
    }

    return contactData;
  }

  private String getNameFromContact(Context context, Uri uri) {
    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[] { Contacts.DISPLAY_NAME }, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(0);
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
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
      this.type   = type;
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

    public final long             id;
    public final String           name;
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
}
