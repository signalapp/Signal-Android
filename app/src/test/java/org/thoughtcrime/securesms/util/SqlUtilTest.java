package org.thoughtcrime.securesms.util;

import android.app.Application;
import android.content.ContentValues;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a != ? OR a IS NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_complexSelection() {
    String   selection = "_id = ? AND (foo = ? OR bar != ?)";
    String[] args      = new String[]{"1", "2", "3"};

    ContentValues values = new ContentValues();
    values.put("a", 4);

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

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

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a != ? OR a IS NULL OR b != ? OR b IS NULL OR c != ? OR c IS NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4"}, updateQuery.getWhereArgs());
  }

  @Test
  public void buildTrueUpdateQuery_nullContentValue() {
    String   selection = "_id = ?";
    String[] args      = new String[]{"1"};

    ContentValues values = new ContentValues();
    values.put("a", (String) null);

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

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

    SqlUtil.Query updateQuery = SqlUtil.buildTrueUpdateQuery(selection, args, values);

    assertEquals("(_id = ?) AND (a NOT NULL OR b != ? OR b IS NULL OR c != ? OR c IS NULL OR d NOT NULL OR e NOT NULL)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildCollectionQuery_single() {
    SqlUtil.Query updateQuery = SqlUtil.buildCollectionQuery("a", Arrays.asList(1));

    assertEquals("a IN (?)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildCollectionQuery_multiple() {
    SqlUtil.Query updateQuery = SqlUtil.buildCollectionQuery("a", Arrays.asList(1, 2, 3));

    assertEquals("a IN (?, ?, ?)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3" }, updateQuery.getWhereArgs());
  }

  @Test
  public void buildCollectionQuery_multipleRecipientIds() {
    SqlUtil.Query updateQuery = SqlUtil.buildCollectionQuery("a", Arrays.asList(RecipientId.from(1), RecipientId.from(2), RecipientId.from(3)));

    assertEquals("a IN (?, ?, ?)", updateQuery.getWhere());
    assertArrayEquals(new String[] { "1", "2", "3" }, updateQuery.getWhereArgs());
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildCollectionQuery_none() {
    SqlUtil.buildCollectionQuery("a", Collections.emptyList());
  }

  @Test
  public void buildCustomCollectionQuery_single_singleBatch() {
    List<String[]> args = new ArrayList<>();
    args.add(SqlUtil.buildArgs(1, 2));

    List<SqlUtil.Query> queries = SqlUtil.buildCustomCollectionQuery("a = ? AND b = ?", args);

    assertEquals(1, queries.size());
    assertEquals("(a = ? AND b = ?)", queries.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2" }, queries.get(0).getWhereArgs());
  }

  @Test
  public void buildCustomCollectionQuery_multiple_singleBatch() {
    List<String[]> args = new ArrayList<>();
    args.add(SqlUtil.buildArgs(1, 2));
    args.add(SqlUtil.buildArgs(3, 4));
    args.add(SqlUtil.buildArgs(5, 6));

    List<SqlUtil.Query> queries = SqlUtil.buildCustomCollectionQuery("a = ? AND b = ?", args);

    assertEquals(1, queries.size());
    assertEquals("(a = ? AND b = ?) OR (a = ? AND b = ?) OR (a = ? AND b = ?)", queries.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4", "5", "6" }, queries.get(0).getWhereArgs());
  }

  @Test
  public void buildCustomCollectionQuery_twoBatches() {
    List<String[]> args = new ArrayList<>();
    args.add(SqlUtil.buildArgs(1, 2));
    args.add(SqlUtil.buildArgs(3, 4));
    args.add(SqlUtil.buildArgs(5, 6));

    List<SqlUtil.Query> queries = SqlUtil.buildCustomCollectionQuery("a = ? AND b = ?", args, 4);

    assertEquals(2, queries.size());
    assertEquals("(a = ? AND b = ?) OR (a = ? AND b = ?)", queries.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4" }, queries.get(0).getWhereArgs());
    assertEquals("(a = ? AND b = ?)", queries.get(1).getWhere());
    assertArrayEquals(new String[] { "5", "6" }, queries.get(1).getWhereArgs());
  }

  @Test
  public void splitStatements_singleStatement() {
    List<String> result = SqlUtil.splitStatements("SELECT * FROM foo;\n");
    assertEquals(Arrays.asList("SELECT * FROM foo"), result);
  }

  @Test
  public void splitStatements_twoStatements() {
    List<String> result = SqlUtil.splitStatements("SELECT * FROM foo;\nSELECT * FROM bar;\n");
    assertEquals(Arrays.asList("SELECT * FROM foo", "SELECT * FROM bar"), result);
  }

  @Test
  public void splitStatements_twoStatementsSeparatedByNewLines() {
    List<String> result = SqlUtil.splitStatements("SELECT * FROM foo;\n\nSELECT * FROM bar;\n");
    assertEquals(Arrays.asList("SELECT * FROM foo", "SELECT * FROM bar"), result);
  }

  @Test
  public void buildBulkInsert_single_singleBatch() {
    List<ContentValues> contentValues = new ArrayList<>();

    ContentValues cv1 = new ContentValues();
    cv1.put("a", 1);
    cv1.put("b", 2);

    contentValues.add(cv1);

    List<SqlUtil.Query> output = SqlUtil.buildBulkInsert("mytable", new String[] { "a", "b"}, contentValues);

    assertEquals(1, output.size());
    assertEquals("INSERT INTO mytable (a, b) VALUES (?, ?)", output.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2" }, output.get(0).getWhereArgs());
  }

  @Test
  public void buildBulkInsert_multiple_singleBatch() {
    List<ContentValues> contentValues = new ArrayList<>();

    ContentValues cv1 = new ContentValues();
    cv1.put("a", 1);
    cv1.put("b", 2);

    ContentValues cv2 = new ContentValues();
    cv2.put("a", 3);
    cv2.put("b", 4);

    contentValues.add(cv1);
    contentValues.add(cv2);

    List<SqlUtil.Query> output = SqlUtil.buildBulkInsert("mytable", new String[] { "a", "b"}, contentValues);

    assertEquals(1, output.size());
    assertEquals("INSERT INTO mytable (a, b) VALUES (?, ?), (?, ?)", output.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4" }, output.get(0).getWhereArgs());
  }

  @Test
  public void buildBulkInsert_twoBatches() {
    List<ContentValues> contentValues = new ArrayList<>();

    ContentValues cv1 = new ContentValues();
    cv1.put("a", 1);
    cv1.put("b", 2);

    ContentValues cv2 = new ContentValues();
    cv2.put("a", 3);
    cv2.put("b", 4);

    ContentValues cv3 = new ContentValues();
    cv3.put("a", 5);
    cv3.put("b", 6);

    contentValues.add(cv1);
    contentValues.add(cv2);
    contentValues.add(cv3);

    List<SqlUtil.Query> output = SqlUtil.buildBulkInsert("mytable", new String[] { "a", "b"}, contentValues, 4);

    assertEquals(2, output.size());

    assertEquals("INSERT INTO mytable (a, b) VALUES (?, ?), (?, ?)", output.get(0).getWhere());
    assertArrayEquals(new String[] { "1", "2", "3", "4" }, output.get(0).getWhereArgs());

    assertEquals("INSERT INTO mytable (a, b) VALUES (?, ?)", output.get(1).getWhere());
    assertArrayEquals(new String[] { "5", "6" }, output.get(1).getWhereArgs());
  }
}
