package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Debug;
import android.provider.ContactsContract;
import android.util.Log;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.util.List;

public class PushFilterCursorWrapper extends CursorWrapper {
  private int[] pushIndex;
  private int[] normalIndex;
  private int count     = 0;
  private int pushCount = 0;
  private int pos       = 0;

  private final ContactAccessor contactAccessor = ContactAccessor.getInstance();

  /*
   * Don't know of a better way to do this without a large filtering through the entire dataset at first
   */
  public PushFilterCursorWrapper(Cursor cursor, Context context) {
    super(cursor);
    this.count = super.getCount();
    this.pushIndex = new int[this.count];
    this.normalIndex = new int[this.count];
    int pushPos = 0;
    int normalPos = 0;
    for (int i = 0; i < this.count; i++) {
      super.moveToPosition(i);


      List<ContactAccessor.NumberData> numbers = contactAccessor.getContactData(context, cursor).numbers;
      if (numbers.size() > 0) {
        try {
          if (Util.isPushTransport(context, Util.canonicalizeNumber(context, numbers.get(0).number)))
            this.pushIndex[pushPos++] = i;
          else
            this.normalIndex[normalPos++] = i;
        } catch (InvalidNumberException ine) {
        }
      }
    }
    this.pushCount = pushPos;
    super.moveToFirst();
  }

  @Override
  public boolean move(int offset) {
    return this.moveToPosition(this.pos + offset);
  }

  @Override
  public boolean moveToNext() {
    return this.moveToPosition(this.pos + 1);
  }

  @Override
  public boolean moveToPrevious() {
    return this.moveToPosition(this.pos - 1);
  }

  @Override
  public boolean moveToFirst() {
    return this.moveToPosition(0);
  }

  @Override
  public boolean moveToLast() {
    return this.moveToPosition(this.count - 1);
  }

  private int getPostFilteredPosition(int preFilteredPosition) {
    return preFilteredPosition < this.pushCount
        ? this.pushIndex[preFilteredPosition]
        : this.normalIndex[preFilteredPosition - pushCount];
  }

  @Override
  public boolean moveToPosition(int position) {
    if (position >= this.count || position < 0)
      return false;
    pos = position;
    return super.moveToPosition(getPostFilteredPosition(position));
  }

  @Override
  public int getCount() {
    return this.count;
  }

  public int getPushCount() {
    return this.pushCount;
  }

  @Override
  public int getPosition() {
    return this.pos;
  }

}