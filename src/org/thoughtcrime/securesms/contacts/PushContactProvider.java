package org.thoughtcrime.securesms.contacts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.content.UriMatcher;

/**
 * Created by Lukas Barth on 01.03.14.
 */
public class PushContactProvider extends ContentProvider {

  private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

  public static class PushContactContract {
    public static final String AUTHORITY = "org.thoughtcrime.provider.pushcontact";
    public static final String PUSHCONTACTS = "pushcontacts";

    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri PUSHCONTACT_URI = Uri.withAppendedPath(AUTHORITY_URI, PUSHCONTACTS);
  }

  static {
    uriMatcher.addURI("org.thoughtcrime.provider.pushcontact", "pushcontacts", 1);
  }

  /*
   * Always return true, indicating that the
   * provider loaded correctly.
   */
  @Override
  public boolean onCreate() {
    return true;
  }

  /*
   * Return an empty String for MIME type
   *
   */
  @Override
  public String getType(Uri uri) {
    return "vnd.android.cursor.dir/vnd.org.thoughtcrime.provider.pushcontact.pushcontacts";
  }

  /*
   * query() always returns no results
   *
   */
  @Override
  public Cursor query(
          Uri uri,
          String[] projection,
          String selection,
          String[] selectionArgs,
          String sortOrder) {
    // We ignore the URI for now since we have exactly one thing we can get..

    ContactAccessor contactAccessor = ContactAccessor.getInstance();
    return contactAccessor.getCursorForContactsWithPush(getContext());
  }

  /*
   * insert() always returns null (no URI)
   *
   * TODO implement adding push contacts via this content provider
   */
  @Override
  public Uri insert(Uri uri, ContentValues values) {
      return null;
  }

  /*
   * delete() always returns "no rows affected" (0)
   *
   * TODO implement deleting push contacts via this content provider
   */
  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
      return 0;
  }

  /*
   * update() always returns "no rows affected" (0)
   *
   * TODO implement updating push contacts via this content provider
   */
  public int update(
          Uri uri,
          ContentValues values,
          String selection,
          String[] selectionArgs) {
      return 0;
  }
}
