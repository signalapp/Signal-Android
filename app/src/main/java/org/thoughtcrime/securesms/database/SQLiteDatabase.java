package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.zetetic.database.SQLException;
import net.zetetic.database.sqlcipher.SQLiteStatement;
import net.zetetic.database.sqlcipher.SQLiteTransactionListener;

import org.signal.core.util.tracing.Tracer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This is a wrapper around {@link net.zetetic.database.sqlcipher.SQLiteDatabase}. There's difficulties
 * making a subclass, so instead we just match the interface. Callers should just need to change
 * their import statements.
 */
public class SQLiteDatabase {

  public static final int CONFLICT_ROLLBACK = 1;
  public static final int CONFLICT_ABORT    = 2;
  public static final int CONFLICT_FAIL     = 3;
  public static final int CONFLICT_IGNORE   = 4;
  public static final int CONFLICT_REPLACE  = 5;
  public static final int CONFLICT_NONE     = 0;

  private static final String KEY_QUERY  = "query";
  private static final String KEY_TABLE  = "table";
  private static final String KEY_THREAD = "thread";
  private static final String NAME_LOCK  = "LOCK";

  private final net.zetetic.database.sqlcipher.SQLiteDatabase wrapped;
  private final Tracer                                        tracer;

  private static final ThreadLocal<Set<Runnable>> POST_TRANSACTION_TASKS = new ThreadLocal<>();
  static {
    POST_TRANSACTION_TASKS.set(new LinkedHashSet<>());
  }

  public SQLiteDatabase(net.zetetic.database.sqlcipher.SQLiteDatabase wrapped) {
    this.wrapped = wrapped;
    this.tracer  = Tracer.getInstance();
  }

  private void traceLockStart() {
    tracer.start(NAME_LOCK, Tracer.TrackId.DB_LOCK, KEY_THREAD, Thread.currentThread().getName());
  }

  private void traceLockEnd() {
    tracer.end(NAME_LOCK, Tracer.TrackId.DB_LOCK);
  }

  private void trace(String methodName, Runnable runnable) {
    tracer.start(methodName);
    runnable.run();
    tracer.end(methodName);
  }

  private void traceSql(String methodName, String query, boolean locked, Runnable returnable) {
    if (locked) {
      traceLockStart();
    }

    tracer.start(methodName, KEY_QUERY, query);
    returnable.run();
    tracer.end(methodName);

    if (locked) {
      traceLockEnd();
    }
  }

  private <E> E traceSql(String methodName, String query, boolean locked, Returnable<E> returnable) {
    return traceSql(methodName, null, query, locked, returnable);
  }

  private <E> E traceSql(String methodName, String table, String query, boolean locked, Returnable<E> returnable) {
    if (locked) {
      traceLockStart();
    }

    Map<String, String> params = new HashMap<>();
    if (query != null) {
      params.put(KEY_QUERY, query);
    }
    if (table != null) {
      params.put(KEY_TABLE, table);
    }

    tracer.start(methodName, params);
    E result = returnable.run();
    tracer.end(methodName);

    if (locked) {
      traceLockEnd();
    }

    return result;
  }

  public net.zetetic.database.sqlcipher.SQLiteDatabase getSqlCipherDatabase() {
    return wrapped;
  }

  /**
   * Allows you to enqueue a task to be run after the active transaction is successfully completed.
   * If the transaction fails, the task is discarded.
   * If there is no current transaction open, the task is run immediately.
   */
  public void runPostSuccessfulTransaction(@NonNull Runnable task) {
    if (wrapped.inTransaction()) {
      getPostTransactionTasks().add(task);
    } else {
      task.run();
    }
  }

  /**
   * Does the same as {@link #runPostSuccessfulTransaction(Runnable)}, except that you can pass in a "dedupe key".
   * There can only be one task enqueued for a given dedupe key. So, if you enqueue a second task with that key, it will be discarded.
   */
  public void runPostSuccessfulTransaction(@NonNull String dedupeKey, @NonNull Runnable task) {
    if (wrapped.inTransaction()) {
      getPostTransactionTasks().add(new DedupedRunnable(dedupeKey, task));
    } else {
      task.run();
    }
  }

  private @NonNull Set<Runnable> getPostTransactionTasks() {
    Set<Runnable> tasks = POST_TRANSACTION_TASKS.get();

    if (tasks == null) {
      tasks = new LinkedHashSet<>();
      POST_TRANSACTION_TASKS.set(tasks);
    }

    return tasks;
  }

  private interface Returnable<E> {
    E run();
  }

  /**
   * Runnable whose equals/hashcode is determined by a key you pass in.
   */
  private static class DedupedRunnable implements Runnable {
    private final String   key;
    private final Runnable runnable;

    protected DedupedRunnable(@NonNull String key, @NonNull Runnable runnable) {
      this.key      = key;
      this.runnable = runnable;
    }

    @Override
    public void run() {
      runnable.run();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final DedupedRunnable that = (DedupedRunnable) o;
      return key.equals(that.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key);
    }
  }


  // =======================================================
  // Traced
  // =======================================================

  public void beginTransaction() {
    traceLockStart();

    if (wrapped.inTransaction()) {
      trace("beginTransaction()", wrapped::beginTransaction);
    } else {
      trace("beginTransaction()", () -> {
        wrapped.beginTransactionWithListener(new SQLiteTransactionListener() {
          @Override
          public void onBegin() { }

          @Override
          public void onCommit() {
            Set<Runnable> tasks = getPostTransactionTasks();
            for (Runnable r : tasks) {
              r.run();
            }
            tasks.clear();
          }

          @Override
          public void onRollback() {
            getPostTransactionTasks().clear();
          }
        });
      });
    }
  }

  public void endTransaction() {
    trace("endTransaction()", wrapped::endTransaction);
    traceLockEnd();
  }

  public void setTransactionSuccessful() {
    trace("setTransactionSuccessful()", wrapped::setTransactionSuccessful);
  }

  public Cursor query(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    return traceSql("query(9)", table, selection, false, () -> wrapped.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor queryWithFactory(net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    return traceSql("queryWithFactory()", table, selection, false, () -> wrapped.queryWithFactory(cursorFactory, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
    return traceSql("query(7)", table, selection, false, () -> wrapped.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
  }

  public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    return traceSql("query(8)", table, selection, false, () -> wrapped.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor rawQuery(String sql, String[] selectionArgs) {
    return traceSql("rawQuery(2a)", sql, false, () -> wrapped.rawQuery(sql, selectionArgs));
  }

  public Cursor rawQuery(String sql, Object[] args) {
    return traceSql("rawQuery(2b)", sql, false,() -> wrapped.rawQuery(sql, args));
  }

  public Cursor rawQueryWithFactory(net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable) {
    return traceSql("rawQueryWithFactory()", sql, false, () -> wrapped.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable));
  }

  public Cursor rawQuery(String sql, String[] selectionArgs, int initialRead, int maxRead) {
    return traceSql("rawQuery(4)", sql, false, () -> rawQuery(sql, selectionArgs, initialRead, maxRead));
  }

  public long insert(String table, String nullColumnHack, ContentValues values) {
    return traceSql("insert()", table, null, true, () -> wrapped.insert(table, nullColumnHack, values));
  }

  public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
    return traceSql("insertOrThrow()", table, null, true, () -> wrapped.insertOrThrow(table, nullColumnHack, values));
  }

  public long replace(String table, String nullColumnHack, ContentValues initialValues) {
    return traceSql("replace()", table, null, true,() -> wrapped.replace(table, nullColumnHack, initialValues));
  }

  public long replaceOrThrow(String table, String nullColumnHack, ContentValues initialValues) throws SQLException {
    return traceSql("replaceOrThrow()", table, null, true, () -> wrapped.replaceOrThrow(table, nullColumnHack, initialValues));
  }

  public long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
    return traceSql("insertWithOnConflict()", table, null, true, () -> wrapped.insertWithOnConflict(table, nullColumnHack, initialValues, conflictAlgorithm));
  }

  public int delete(String table, String whereClause, String[] whereArgs) {
    return traceSql("delete()", table, whereClause, true, () -> wrapped.delete(table, whereClause, whereArgs));
  }

  public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
    return traceSql("update()", table, whereClause, true, () -> wrapped.update(table, values, whereClause, whereArgs));
  }

  public int updateWithOnConflict(String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
    return traceSql("updateWithOnConflict()", table, whereClause, true, () -> wrapped.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm));
  }

  public void execSQL(String sql) throws SQLException {
    traceSql("execSQL(1)", sql, true, () -> wrapped.execSQL(sql));
  }

  public void rawExecSQL(String sql) {
    traceSql("rawExecSQL()", sql, true, () -> wrapped.rawExecSQL(sql));
  }

  public void execSQL(String sql, Object[] bindArgs) throws SQLException {
    traceSql("execSQL(2)", sql, true, () -> wrapped.execSQL(sql, bindArgs));
  }


  // =======================================================
  // Ignored
  // =======================================================

  public boolean enableWriteAheadLogging() {
    return wrapped.enableWriteAheadLogging();
  }

  public void disableWriteAheadLogging() {
    wrapped.disableWriteAheadLogging();
  }

  public boolean isWriteAheadLoggingEnabled() {
    return wrapped.isWriteAheadLoggingEnabled();
  }

  public void setForeignKeyConstraintsEnabled(boolean enable) {
    wrapped.setForeignKeyConstraintsEnabled(enable);
  }

  public void beginTransactionWithListener(SQLiteTransactionListener transactionListener) {
    wrapped.beginTransactionWithListener(transactionListener);
  }

  public void beginTransactionNonExclusive() {
    wrapped.beginTransactionNonExclusive();
  }

  public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener) {
    wrapped.beginTransactionWithListenerNonExclusive(transactionListener);
  }

  public boolean inTransaction() {
    return wrapped.inTransaction();
  }

  public boolean isDbLockedByCurrentThread() {
    return wrapped.isDbLockedByCurrentThread();
  }

  public boolean isDbLockedByOtherThreads() {
    return wrapped.isDbLockedByOtherThreads();
  }

  public boolean yieldIfContendedSafely() {
    return wrapped.yieldIfContendedSafely();
  }

  public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) {
    return wrapped.yieldIfContendedSafely(sleepAfterYieldDelay);
  }

  public int getVersion() {
    return wrapped.getVersion();
  }

  public void setVersion(int version) {
    wrapped.setVersion(version);
  }

  public long getMaximumSize() {
    return wrapped.getMaximumSize();
  }

  public long setMaximumSize(long numBytes) {
    return wrapped.setMaximumSize(numBytes);
  }

  public long getPageSize() {
    return wrapped.getPageSize();
  }

  public void setPageSize(long numBytes) {
    wrapped.setPageSize(numBytes);
  }

  public SQLiteStatement compileStatement(String sql) throws SQLException {
    return wrapped.compileStatement(sql);
  }

  public boolean isReadOnly() {
    return wrapped.isReadOnly();
  }

  public boolean isOpen() {
    return wrapped.isOpen();
  }

  public boolean needUpgrade(int newVersion) {
    return wrapped.needUpgrade(newVersion);
  }

  public final String getPath() {
    return wrapped.getPath();
  }

  public void setLocale(Locale locale) {
    wrapped.setLocale(locale);
  }
}
