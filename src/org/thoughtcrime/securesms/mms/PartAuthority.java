package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.providers.PartProvider;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING   = "content://org.thoughtcrime.securesms/part";
  private static final String THUMB_URI_STRING  = "content://org.thoughtcrime.securesms/thumb";
  public  static final Uri    PART_CONTENT_URI  = Uri.parse(PART_URI_STRING);
  public  static final Uri    THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);

  private static final int PART_ROW  = 1;
  private static final int THUMB_ROW = 2;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.thoughtcrime.securesms", "part/#", PART_ROW);
    uriMatcher.addURI("org.thoughtcrime.securesms", "thumb/#", THUMB_ROW);
  }

  public static InputStream getPartStream(Context context, MasterSecret masterSecret, Uri uri)
      throws IOException
  {
    PartDatabase partDatabase = DatabaseFactory.getPartDatabase(context);
    int          match        = uriMatcher.match(uri);

    try {
      switch (match) {
      case PART_ROW:  return partDatabase.getPartStream(masterSecret, ContentUris.parseId(uri));
      case THUMB_ROW: return partDatabase.getThumbnailStream(masterSecret, ContentUris.parseId(uri));
      default:        return context.getContentResolver().openInputStream(uri);
      }
    } catch (SecurityException se) {
      throw new IOException(se);
    }
  }

  public static Uri getPublicPartUri(Uri uri) {
    return ContentUris.withAppendedId(PartProvider.CONTENT_URI, ContentUris.parseId(uri));
  }

  public static Uri getThumbnailUri(long partId) {
    return ContentUris.withAppendedId(THUMB_CONTENT_URI, partId);
  }
}
