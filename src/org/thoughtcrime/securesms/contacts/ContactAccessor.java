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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.CursorLoader;

import org.thoughtcrime.securesms.crypto.IdentityKey;

import java.util.LinkedList;
import java.util.List;

/**
 * Android changed their contacts API pretty heavily between
 * 1.x and 2.x.  This class provides a common interface to both
 * API operations, using a singleton pattern that will Class.forName
 * the correct one so we don't trigger NoClassDefFound exceptions on
 * old platforms.
 *
 * @author Moxie Marlinspike
 */

public abstract class ContactAccessor {
  public static final int UNIQUE_ID    = 0;
  public static final int DISPLAY_NAME = 1;

  private static final ContactAccessor sInstance = new ContactAccessorNewApi();

  public static synchronized ContactAccessor getInstance() {
    return sInstance;
  }

  public abstract NameAndNumber		   getNameAndNumberFromContact(Context context, Uri uri);
  public abstract String                 getNameFromContact(Context context, Uri uri);
  public abstract IdentityKey            importIdentityKey(Context context, Uri uri);
  public abstract void                   insertIdentityKey(Context context, Uri uri, IdentityKey identityKey);
  public abstract Intent                 getIntentForContactSelection();
  public abstract List<String>           getNumbersForThreadSearchFilter(String constraint, ContentResolver contentResolver);
  public abstract List<ContactData>      getGroupMembership(Context context, long groupId);
  public abstract Cursor                 getCursorForContactGroups(Context context);
  public abstract CursorLoader           getCursorLoaderForContactGroups(Context context);
  public abstract CursorLoader           getCursorLoaderForContactsWithNumbers(Context context);
  public abstract Cursor                 getCursorForContactsWithNumbers(Context context);
  public abstract GroupData              getGroupData(Context context, Cursor cursor);
  public abstract ContactData            getContactData(Context context, Cursor cursor);
  public abstract Cursor                 getCursorForRecipientFilter(CharSequence constraint, ContentResolver mContentResolver);
  public abstract CharSequence           phoneTypeToString(Context mContext, int type, CharSequence label);
  public abstract String                 getNameForNumber(Context context, String number);
  public abstract Uri                    getContactsUri();

  public static class NumberData implements Parcelable {

    public static final Parcelable.Creator<NumberData> CREATOR = new Parcelable.Creator<NumberData>() {
      public NumberData createFromParcel(Parcel in) {
        return new NumberData(in);
      }

      public NumberData[] newArray(int size) {
        return new NumberData[size];
      }
    };

    public String number;
    public String type;

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

  public static class GroupData {
    public long id;
    public String name;
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

    public long id;
    public String name;
    public List<NumberData> numbers;

    public ContactData() {}

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
