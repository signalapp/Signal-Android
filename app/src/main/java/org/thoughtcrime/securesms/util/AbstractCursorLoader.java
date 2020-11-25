package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import androidx.loader.content.AsyncTaskLoader;

/**
 * A Loader similar to CursorLoader that doesn't require queries to go through the ContentResolver
 * to get the benefits of reloading when content has changed.
 */
public abstract class AbstractCursorLoader extends AsyncTaskLoader<Cursor> {

  @SuppressWarnings("unused")
  private static final String TAG = AbstractCursorLoader.class.getSimpleName();

  @SuppressLint("StaticFieldLeak")
  protected final Context                  context;
  private   final ForceLoadContentObserver observer;
  protected       Cursor                   cursor;

  public AbstractCursorLoader(Context context) {
    super(context);
    this.context  = context.getApplicationContext();
    this.observer = new ForceLoadContentObserver();
  }

  public abstract Cursor getCursor();

  @Override
  public void deliverResult(Cursor newCursor) {
    if (isReset()) {
      if (newCursor != null) {
        newCursor.close();
      }
      return;
    }
    Cursor oldCursor = this.cursor;

    this.cursor = newCursor;

    if (isStarted()) {
      super.deliverResult(newCursor);
    }
    if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
      oldCursor.close();
    }
  }

  @Override
  protected void onStartLoading() {
    if (cursor != null) {
      deliverResult(cursor);
    }
    if (takeContentChanged() || cursor == null) {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }

  @Override
  public void onCanceled(Cursor cursor) {
    if (cursor != null && !cursor.isClosed()) {
      cursor.close();
    }
  }

  @Override
  public Cursor loadInBackground() {
    Cursor newCursor = getCursor();
    if (newCursor != null) {
      newCursor.getCount();
      newCursor.registerContentObserver(observer);
    }
    return newCursor;
  }

  @Override
  protected void onReset() {
    super.onReset();

    onStopLoading();

    if (cursor != null && !cursor.isClosed()) {
      cursor.close();
    }
    cursor = null;
  }

}
