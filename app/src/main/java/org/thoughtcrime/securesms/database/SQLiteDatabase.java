package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.os.CancellationSignal;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;

import net.zetetic.database.sqlcipher.SQLiteStatement;
import net.zetetic.database.sqlcipher.SQLiteQueryBuilder;
import net.zetetic.database.sqlcipher.SQLiteTransactionListener;

import org.signal.core.util.logging.Log;
import org.signal.core.util.tracing.Tracer;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This is a wrapper around {@link net.zetetic.database.sqlcipher.SQLiteDatabase}. There's difficulties
 * making a subclass, so instead we just match the interface. Callers should just need to change
 * their import statements.
 */
public class SQLiteDatabase implements SupportSQLiteDatabase {

  private static final String TAG = Log.tag(SQLiteDatabase.class);

  private static final long SLOW_WRITE_LOCK_WAIT_MS = TimeUnit.SECONDS.toMillis(3);
  private static final long SLOW_TRANSACTION_HOLD_MS = 750;
  private static final long SLOW_DIRECT_WRITE_MS = 250;
  private static final long SLOW_DIRECT_DELETE_MS = TimeUnit.SECONDS.toMillis(1);
  private static final long SLOW_QUERY_MS = TimeUnit.SECONDS.toMillis(1);

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

  private static volatile boolean slowWriteLoggingEnabled = false;

  private static final ThreadLocal<Long>          TRANSACTION_HOLD_START_NS = new ThreadLocal<>();
  private static final ThreadLocal<Set<Runnable>> PENDING_POST_SUCCESSFUL_TRANSACTION_TASKS;
  private static final ThreadLocal<Set<Runnable>> POST_SUCCESSFUL_TRANSACTION_TASKS;

  static {
    PENDING_POST_SUCCESSFUL_TRANSACTION_TASKS = new ThreadLocal<>();
    POST_SUCCESSFUL_TRANSACTION_TASKS         = new ThreadLocal<>();

    PENDING_POST_SUCCESSFUL_TRANSACTION_TASKS.set(new LinkedHashSet<>());
  }

  public static void setSlowWriteLoggingEnabled(boolean enabled) {
    slowWriteLoggingEnabled = enabled;
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
    traceSql(methodName, query, locked, null, null, returnable);
  }

  private void traceSql(String methodName, String query, boolean locked, String queryPlanSql, Object[] queryPlanArgs, Runnable returnable) {
    if (locked) {
      traceLockStart();
    }

    tracer.start(methodName, KEY_QUERY, query);
    long startNs = slowWriteLoggingEnabled && locked ? System.nanoTime() : 0L;
    returnable.run();
    if (slowWriteLoggingEnabled && locked) {
      warnIfSlowDirectWrite(methodName, null, query, queryPlanSql, queryPlanArgs, startNs);
    }
    tracer.end(methodName);

    if (locked) {
      traceLockEnd();
    }
  }

  private <E> E traceSql(String methodName, String query, boolean locked, Returnable<E> returnable) {
    return traceSql(methodName, null, query, locked, returnable);
  }

  private <E> E traceSql(String methodName, String table, String query, boolean locked, Returnable<E> returnable) {
    return traceSql(methodName, table, query, locked, null, null, returnable);
  }

  private <E> E traceSql(String methodName, String table, String query, boolean locked, String queryPlanSql, Object[] queryPlanArgs, Returnable<E> returnable) {
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
    long startNs = slowWriteLoggingEnabled ? System.nanoTime() : 0L;
    E result = returnable.run();
    if (result instanceof Cursor) {
      // Triggers filling the window (which is about to be done anyway), but lets us capture that time inside the trace
      ((Cursor) result).getCount();
    }
    if (slowWriteLoggingEnabled) {
      if (locked) {
        warnIfSlowDirectWrite(methodName, table, query, queryPlanSql, queryPlanArgs, startNs);
      } else {
        warnIfSlowQuery(methodName, table, query, queryPlanSql, queryPlanArgs, startNs);
      }
    }
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
      getPendingPostSuccessfulTransactionTasks().add(task);
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
      getPendingPostSuccessfulTransactionTasks().add(new DedupedRunnable(dedupeKey, task));
    } else {
      task.run();
    }
  }

  private @NonNull Set<Runnable> getPendingPostSuccessfulTransactionTasks() {
    Set<Runnable> tasks = PENDING_POST_SUCCESSFUL_TRANSACTION_TASKS.get();

    if (tasks == null) {
      tasks = new LinkedHashSet<>();
      PENDING_POST_SUCCESSFUL_TRANSACTION_TASKS.set(tasks);
    }

    return tasks;
  }

  private @NonNull Set<Runnable> getPostSuccessfulTransactionTasks() {
    Set<Runnable> tasks = POST_SUCCESSFUL_TRANSACTION_TASKS.get();

    if (tasks == null) {
      tasks = new LinkedHashSet<>();
      POST_SUCCESSFUL_TRANSACTION_TASKS.set(tasks);
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
  // Overrides
  // =======================================================

  @Override
  public void beginTransactionWithListener(android.database.sqlite.SQLiteTransactionListener transactionListener) {
    beginTransactionWithListener(new ConvertedTransactionListener(transactionListener));
  }

  @Override
  public void beginTransactionWithListenerNonExclusive(android.database.sqlite.SQLiteTransactionListener transactionListener) {
    beginTransactionWithListenerNonExclusive(new ConvertedTransactionListener(transactionListener));
  }

  @Override
  public Cursor query(String query) {
    return rawQuery(query, null);
  }

  @Override
  public Cursor query(String query, Object[] bindArgs) {
    return rawQuery(query, bindArgs);
  }

  @Override
  public Cursor query(SupportSQLiteQuery query) {
    DatabaseMonitor.onSql(query.getSql(), null);
    return traceSql("query(SupportSQLiteQuery)", null, query.getSql(), false, query.getSql(), null, () -> wrapped.query(query));
  }

  @Override
  public Cursor query(SupportSQLiteQuery query, CancellationSignal cancellationSignal) {
    DatabaseMonitor.onSql(query.getSql(), null);
    return traceSql("query(SupportSQLiteQuery, CancellationSignal)", null, query.getSql(), false, query.getSql(), null, () -> wrapped.query(query, cancellationSignal));
  }

  @Override
  public long insert(String table, int conflictAlgorithm, ContentValues values) throws android.database.SQLException {
    return insertWithOnConflict(table, null, values, conflictAlgorithm);
  }

  @Override
  public int delete(String table, String whereClause, Object[] whereArgs) {
    return delete(table, whereClause, (String[]) whereArgs);
  }

  @Override
  public int update(String table, int conflictAlgorithm, ContentValues values, String whereClause, Object[] whereArgs) {
    return updateWithOnConflict(table, values, whereClause, (String[]) whereArgs, conflictAlgorithm);
  }

  @Override
  public void setMaxSqlCacheSize(int cacheSize) {
    wrapped.setMaxSqlCacheSize(cacheSize);
  }

  @Override
  public List<Pair<String, String>> getAttachedDbs() {
    return wrapped.getAttachedDbs();
  }

  @Override
  public boolean isDatabaseIntegrityOk() {
    return wrapped.isDatabaseIntegrityOk();
  }

  @Override
  public void close() throws IOException {
    wrapped.close();
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
        long waitStartNs = slowWriteLoggingEnabled ? System.nanoTime() : 0L;
        wrapped.beginTransactionWithListener(new SQLiteTransactionListener() {
          @Override
          public void onBegin() { }

          @Override
          public void onCommit() {
            Set<Runnable> pendingTasks = getPendingPostSuccessfulTransactionTasks();
            Set<Runnable> tasks        = getPostSuccessfulTransactionTasks();
            tasks.clear();
            tasks.addAll(pendingTasks);
            pendingTasks.clear();
          }

          @Override
          public void onRollback() {
            getPendingPostSuccessfulTransactionTasks().clear();
          }
        });
        if (slowWriteLoggingEnabled) {
          long waitMs = (System.nanoTime() - waitStartNs) / 1_000_000L;
          if (waitMs >= SLOW_WRITE_LOCK_WAIT_MS) {
            Log.w(TAG, "Slow write-lock acquire: waited " + waitMs + "ms to BEGIN", new Throwable());
          }
          TRANSACTION_HOLD_START_NS.set(System.nanoTime());
        }
      });
    }
  }

  public void endTransaction() {
    Long holdStartNs = slowWriteLoggingEnabled ? TRANSACTION_HOLD_START_NS.get() : null;
    trace("endTransaction()", wrapped::endTransaction);
    traceLockEnd();
    if (holdStartNs != null && !wrapped.inTransaction()) {
      TRANSACTION_HOLD_START_NS.remove();
      if (slowWriteLoggingEnabled) {
        long holdMs = (System.nanoTime() - holdStartNs) / 1_000_000L;
        if (holdMs >= SLOW_TRANSACTION_HOLD_MS) {
          Log.w(TAG, "Slow transaction: held write lock for " + holdMs + "ms", new Throwable());
          SlowTransactionInternalNotifier.onSlowEvent();
        }
      }
    }
    Set<Runnable> tasks = getPostSuccessfulTransactionTasks();
    for (Runnable r : new HashSet<>(tasks)) {
      r.run();
    }
    tasks.clear();
  }

  public void setTransactionSuccessful() {
    trace("setTransactionSuccessful()", wrapped::setTransactionSuccessful);
  }

  public Cursor query(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    DatabaseMonitor.onQuery(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    return traceSql("query(9)", table, selection, false, buildQueryPlanSql(distinct, table, columns, selection, groupBy, having, orderBy, limit), selectionArgs, () -> wrapped.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor queryWithFactory(net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    DatabaseMonitor.onQuery(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    return traceSql("queryWithFactory()", table, selection, false, buildQueryPlanSql(distinct, table, columns, selection, groupBy, having, orderBy, limit), selectionArgs, () -> wrapped.queryWithFactory(cursorFactory, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
    DatabaseMonitor.onQuery(false, table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
    return traceSql("query(7)", table, selection, false, buildQueryPlanSql(false, table, columns, selection, groupBy, having, orderBy, null), selectionArgs, () -> wrapped.query(table, columns, selection, selectionArgs, groupBy, having, orderBy));
  }

  public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
    DatabaseMonitor.onQuery(false, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    return traceSql("query(8)", table, selection, false, buildQueryPlanSql(false, table, columns, selection, groupBy, having, orderBy, limit), selectionArgs, () -> wrapped.query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit));
  }

  public Cursor rawQuery(String sql, String[] selectionArgs) {
    DatabaseMonitor.onSql(sql, selectionArgs);
    return traceSql("rawQuery(2a)", null, sql, false, sql, selectionArgs, () -> wrapped.rawQuery(sql, selectionArgs));
  }

  public Cursor rawQuery(String sql, Object... args) {
    DatabaseMonitor.onSql(sql, args);
    return traceSql("rawQuery(2b)", null, sql, false, sql, args, () -> wrapped.rawQuery(sql, args));
  }

  public Cursor rawQueryWithFactory(net.zetetic.database.sqlcipher.SQLiteDatabase.CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable) {
    DatabaseMonitor.onSql(sql, selectionArgs);
    return traceSql("rawQueryWithFactory()", null, sql, false, sql, selectionArgs, () -> wrapped.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable));
  }

  public Cursor rawQuery(String sql, String[] selectionArgs, int initialRead, int maxRead) {
    DatabaseMonitor.onSql(sql, selectionArgs);
    return traceSql("rawQuery(4)", null, sql, false, sql, selectionArgs, () -> rawQuery(sql, selectionArgs, initialRead, maxRead));
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
    DatabaseMonitor.onDelete(table, whereClause, whereArgs);
    return traceSql("delete()", table, whereClause, true, buildDeletePlanSql(table, whereClause), whereArgs, () -> wrapped.delete(table, whereClause, whereArgs));
  }

  public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
    DatabaseMonitor.onUpdate(table, values, whereClause, whereArgs);
    return traceSql("update()", table, whereClause, true, buildUpdatePlanSql(table, values, whereClause, CONFLICT_NONE), buildUpdatePlanArgs(values, whereArgs), () -> wrapped.update(table, values, whereClause, whereArgs));
  }

  public int updateWithOnConflict(String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
    DatabaseMonitor.onUpdate(table, values, whereClause, whereArgs);
    return traceSql("updateWithOnConflict()", table, whereClause, true, buildUpdatePlanSql(table, values, whereClause, conflictAlgorithm), buildUpdatePlanArgs(values, whereArgs), () -> wrapped.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm));
  }

  public void execSQL(String sql) throws SQLException {
    DatabaseMonitor.onSql(sql, null);
    traceSql("execSQL(1)", sql, true, () -> wrapped.execSQL(sql));
  }

  public void rawExecSQL(String sql) {
    DatabaseMonitor.onSql(sql, null);
    traceSql("rawExecSQL()", sql, true, () -> wrapped.rawExecSQL(sql));
  }

  public void execSQL(String sql, Object[] bindArgs) throws SQLException {
    DatabaseMonitor.onSql(sql, null);
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

  private static String buildQueryPlanSql(boolean distinct, String table, String[] columns, String selection, String groupBy, String having, String orderBy, String limit) {
    try {
      return SQLiteQueryBuilder.buildQueryString(distinct, table, columns, selection, groupBy, having, orderBy, limit);
    } catch (Throwable t) {
      return null;
    }
  }

  private static String buildDeletePlanSql(String table, String whereClause) {
    try {
      StringBuilder sql = new StringBuilder(120);
      sql.append("DELETE FROM ").append(table);
      if (whereClause != null && whereClause.length() > 0) {
        sql.append(" WHERE ").append(whereClause);
      }
      return sql.toString();
    } catch (Throwable t) {
      return null;
    }
  }

  private static String buildUpdatePlanSql(String table, ContentValues values, String whereClause, int conflictAlgorithm) {
    try {
      StringBuilder sql = new StringBuilder(120);
      sql.append("UPDATE").append(getConflictClause(conflictAlgorithm)).append(" ").append(table).append(" SET ");

      boolean needsSeparator = false;
      for (Map.Entry<String, Object> entry : values.valueSet()) {
        if (needsSeparator) {
          sql.append(",");
        }
        sql.append(entry.getKey()).append("=?");
        needsSeparator = true;
      }

      if (whereClause != null && whereClause.length() > 0) {
        sql.append(" WHERE ").append(whereClause);
      }

      return sql.toString();
    } catch (Throwable t) {
      return null;
    }
  }

  private static Object[] buildUpdatePlanArgs(ContentValues values, String[] whereArgs) {
    try {
      int      valuesSize = values.size();
      int      whereSize  = whereArgs != null ? whereArgs.length : 0;
      Object[] bindArgs   = new Object[valuesSize + whereSize];
      int      index      = 0;

      for (Map.Entry<String, Object> entry : values.valueSet()) {
        bindArgs[index++] = entry.getValue();
      }

      if (whereArgs != null) {
        for (String whereArg : whereArgs) {
          bindArgs[index++] = whereArg;
        }
      }

      return bindArgs;
    } catch (Throwable t) {
      return null;
    }
  }

  private static String getConflictClause(int conflictAlgorithm) {
    switch (conflictAlgorithm) {
      case CONFLICT_ROLLBACK:
        return " OR ROLLBACK";
      case CONFLICT_ABORT:
        return " OR ABORT";
      case CONFLICT_FAIL:
        return " OR FAIL";
      case CONFLICT_IGNORE:
        return " OR IGNORE";
      case CONFLICT_REPLACE:
        return " OR REPLACE";
      case CONFLICT_NONE:
      default:
        return "";
    }
  }

  private void warnIfSlowDirectWrite(String methodName, String table, String query, String queryPlanSql, Object[] queryPlanArgs, long startNs) {
    if (!slowWriteLoggingEnabled || wrapped.inTransaction()) {
      return;
    }

    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

    long threshold = "delete()".equals(methodName) ? SLOW_DIRECT_DELETE_MS : SLOW_DIRECT_WRITE_MS;

    if (elapsedMs >= threshold) {
      Log.w(TAG, "Slow direct write: " + methodName + " on " + table + " took " + elapsedMs + "ms (query=" + query + ")", new Throwable());
      logQueryPlan(methodName, queryPlanSql, queryPlanArgs);
    }
  }

  private void warnIfSlowQuery(String methodName, String table, String query, String queryPlanSql, Object[] queryPlanArgs, long startNs) {
    if (!slowWriteLoggingEnabled) {
      return;
    }

    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

    if (elapsedMs >= SLOW_QUERY_MS) {
      Log.w(TAG, "Slow query: " + methodName + " on " + table + " took " + elapsedMs + "ms (query=" + query + ")", new Throwable());
      logQueryPlan(methodName, queryPlanSql, queryPlanArgs);
      SlowTransactionInternalNotifier.onSlowEvent();
    }
  }

  private void logQueryPlan(String methodName, String queryPlanSql, Object[] queryPlanArgs) {
    if (queryPlanSql == null) {
      return;
    }

    try (Cursor cursor = queryPlanArgs != null ? wrapped.rawQuery("EXPLAIN QUERY PLAN " + queryPlanSql, queryPlanArgs)
                                               : wrapped.rawQuery("EXPLAIN QUERY PLAN " + queryPlanSql, (String[]) null))
    {
      StringBuilder plan = new StringBuilder();
      while (cursor.moveToNext()) {
        if (plan.length() > 0) {
          plan.append('\n');
        }
        plan.append(cursor.getInt(0))
            .append('|')
            .append(cursor.getInt(1))
            .append('|')
            .append(cursor.getInt(2))
            .append('|')
            .append(cursor.getString(3));
      }

      Log.w(TAG, "Slow query plan: " + methodName + " (query=" + queryPlanSql + ")\n" + plan);
    } catch (Throwable t) {
      Log.w(TAG, "Failed to log slow query plan: " + methodName + " (query=" + queryPlanSql + ")", t);
    }
  }

  private static class ConvertedTransactionListener implements SQLiteTransactionListener {

    private final android.database.sqlite.SQLiteTransactionListener listener;

    ConvertedTransactionListener(android.database.sqlite.SQLiteTransactionListener listener) {
      this.listener = listener;
    }

    @Override
    public void onBegin() {
      listener.onBegin();
    }

    @Override
    public void onCommit() {
      listener.onCommit();
    }

    @Override
    public void onRollback() {
      listener.onRollback();
    }
  }
}
