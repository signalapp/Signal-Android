package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.providers.CaptureProvider;
import org.thoughtcrime.securesms.providers.PartProvider;
import org.thoughtcrime.securesms.providers.SingleUseBlobProvider;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING   = "content://org.thoughtcrime.securesms/part";
  private static final String THUMB_URI_STRING  = "content://org.thoughtcrime.securesms/thumb";
  private static final Uri    PART_CONTENT_URI  = Uri.parse(PART_URI_STRING);
  private static final Uri    THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);

  private static final int PART_ROW       = 1;
  private static final int THUMB_ROW      = 2;
  private static final int CAPTURE_ROW    = 3;
  private static final int SINGLE_USE_ROW = 4;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.thoughtcrime.securesms", "part/*/#", PART_ROW);
    uriMatcher.addURI("org.thoughtcrime.securesms", "thumb/*/#", THUMB_ROW);
    uriMatcher.addURI(CaptureProvider.AUTHORITY, CaptureProvider.EXPECTED_PATH, CAPTURE_ROW);
    uriMatcher.addURI(SingleUseBlobProvider.AUTHORITY, SingleUseBlobProvider.PATH, SINGLE_USE_ROW);
  }

  public static InputStream getAttachmentStream(@NonNull Context context, @NonNull MasterSecret masterSecret, @NonNull Uri uri)
      throws IOException
  {
    int match = uriMatcher.match(uri);
    try {
      switch (match) {
      case PART_ROW:
        PartUriParser partUri = new PartUriParser(uri);
        return DatabaseFactory.getPartDatabase(context).getAttachmentStream(masterSecret, partUri.getPartId());
      case THUMB_ROW:
        partUri = new PartUriParser(uri);
        return DatabaseFactory.getPartDatabase(context).getThumbnailStream(masterSecret, partUri.getPartId());
      case CAPTURE_ROW:
        return CaptureProvider.getInstance(context).getStream(masterSecret, ContentUris.parseId(uri));
      case SINGLE_USE_ROW:
        return SingleUseBlobProvider.getInstance().getStream(ContentUris.parseId(uri));
      default:
        return context.getContentResolver().openInputStream(uri);
      }
    } catch (SecurityException se) {
      throw new IOException(se);
    }
  }

  public static Uri getAttachmentPublicUri(Uri uri) {
    PartUriParser partUri = new PartUriParser(uri);
    return PartProvider.getContentUri(partUri.getPartId());
  }

  public static Uri getAttachmentDataUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(THUMB_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }
}
