package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.content.ContentValues;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class SqlUtilTest {

  @Test
  public void buildTrueUpdateQuery_simple() {
    String   selection = "_id = ?";
    String[] args      = new String[]{"1"};

    ContentValues values = new ContentValues();
    values.put("a", 2);

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a != ? OR a IS NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_complexSelection() {
    String   selection = "_id = ? AND (foo = ? OR bar != ?)";
    String[] args      = new String[]{"1", "2", "3"};

    ContentValues values = new ContentValues();
    values.put("a", 4);

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ? AND (foo = ? OR bar != ?)) AND (a != ? OR a IS NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_multipleContentValues() {
    String   selection = "_id = ?";
    String[] args      = new String[]{"1"};

    ContentValues values = new ContentValues();
    values.put("a", 2);
    values.put("b", 3);
    values.put("c", 4);

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a != ? OR a IS NULL OR b != ? OR b IS NULL OR c != ? OR c IS NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4"}, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_nullContentValue() {
    String   selection = "_id = ?";
    String[] args      = new String[]{"1"};

    ContentValues values = new ContentValues();
    values.put("a", (String) null);

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a NOT NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_complexContentValue() {
    String   selection = "_id = ?";
    String[] args      = new String[]{"1"};

    ContentValues values = new ContentValues();
    values.put("a", (String) null);
    values.put("b", 2);
    values.put("c", 3);
    values.put("d", (String) null);
    values.put("e", (String) null);

    SqlUtil.UpdateQuery updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a NOT NULL OR b != ? OR b IS NULL OR c != ? OR c IS NULL OR d NOT NULL OR e NOT NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3" }, updateQuery.getWhereArgs());
  }
}
