package org.thoughtcrime.redphone.util;

import android.database.Cursor;

import java.util.LinkedList;
import java.util.List;

public class FilteredCursorFactory {

  public interface CursorFilter {
    public boolean isIncluded(Cursor cursor);
  }

  public static Cursor getFilteredCursor(Cursor cursor, CursorFilter filter) {
    List<Integer> map = new LinkedList<Integer>();

    while (cursor.moveToNext()) {
      if (filter.isIncluded(cursor)) {
        map.add(cursor.getPosition());
      }
    }

    return FilteredCursor.createUsingFilter(cursor, toArray(map));
  }

  private static int[] toArray(List<Integer> map) {
    int[] array = new int[map.size()];
    int   index = 0;

    for (int position : map) {
      array[index++] = position;
    }

    return array;
  }

}
