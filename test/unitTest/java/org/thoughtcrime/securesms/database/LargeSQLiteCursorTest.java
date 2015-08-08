package org.thoughtcrime.securesms.database;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.thoughtcrime.securesms.BaseUnitTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class LargeSQLiteCursorTest extends BaseUnitTest {
  @Test public void testSingleCursor() throws Exception {
    LargeSQLiteCursor cursor = new LargeSQLiteCursor(getMockDatabase(1), "select whatever from wherever where something is something else", 1);
    assertThat(cursor.onMove(-1, 0)).isTrue();
    assertThat(cursor.getString(0)).isEqualTo("0");
  }

  @Test public void testMultipleCursors() throws Exception {
    LargeSQLiteCursor cursor = new LargeSQLiteCursor(getMockDatabase(50), "select whatever from wherever where something is something else", 10);
    assertThat(cursor.onMove(-1, 0)).isTrue();
    assertThat(cursor.getString(0)).isEqualTo("0");

    assertThat(cursor.onMove(0, 14)).isTrue();
    assertThat(cursor.getString(0)).isEqualTo("10");

    assertThat(cursor.onMove(14, 5)).isTrue();
    assertThat(cursor.getString(0)).isEqualTo("0");

    assertThat(cursor.onMove(5, 49)).isTrue();
    assertThat(cursor.getString(0)).isEqualTo("40");

    assertThat(cursor.onMove(49, 50)).isFalse();
  }

  @Test public void testCursorEviction() throws Exception {
    LargeSQLiteCursor cursor = new LargeSQLiteCursor(getMockDatabase(100), "select whatever from wherever where something is something else", 10);
    assertThat(cursor.onMove(-1, 0)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(0);
    assertThat(cursor.onMove(0, 10)).isTrue();
    assertThat(cursor.onMove(10, 20)).isTrue();
    assertThat(cursor.onMove(20, 30)).isTrue();
    assertThat(cursor.onMove(30, 40)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(1);
    assertThat(cursor.onMove(40, 50)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(2);
    assertThat(cursor.onMove(50, 30)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(2);
    assertThat(cursor.onMove(30, 60)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(3);
    assertThat(cursor.onMove(60, 70)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(4);
    assertThat(cursor.onMove(70, 1)).isTrue();
    assertThat(cursor.getLoadedCursors().evictionCount()).isEqualTo(5);
  }

  private Cursor getMockCountCursor(int count) {
    Cursor cursor = mock(AbstractCursor.class);
    when(cursor.moveToFirst()).thenReturn(true);
    when(cursor.getInt(0)).thenReturn(count);
    return cursor;
  }

  private Cursor getMockWindowCursor(String firstColumn) {
    Cursor cursor = mock(AbstractCursor.class);
    when(cursor.moveToPosition(anyInt())).thenReturn(true);
    when(cursor.getString(eq(0))).thenReturn(firstColumn);
    return cursor;
  }

  private SQLiteDatabase getMockDatabase(int count) {
    SQLiteDatabase mockDb = mock(SQLiteDatabase.class);
    Cursor countCursor = getMockCountCursor(count);
    when(mockDb.rawQuery(startsWith("SELECT COUNT"), (String[])isNull())).thenReturn(countCursor);
    when(mockDb.rawQuery(matches(".*LIMIT \\d+ OFFSET \\d+.*"), (String[])isNull())).thenAnswer(new Answer<Cursor>() {
      @Override public Cursor answer(InvocationOnMock invocation) throws Throwable {
        String query = (String)invocation.getArguments()[0];
        Matcher matcher = Pattern.compile("OFFSET (\\d+)").matcher(query);
        if (matcher.find()) {
          return getMockWindowCursor(matcher.group(1));
        }
        return null;
      }
    });
    return mockDb;
  }
}
