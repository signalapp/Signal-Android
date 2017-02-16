/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.MediaColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.mms.PersistentBlobUriParser;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PartProvider extends ContentProvider {
  private static final String TAG = PartProvider.class.getSimpleName();

  private static final String PART_URI_STRING       = "content://org.thoughtcrime.provider.securesms/part";
  private static final String PERSISTENT_URI_STRING = "content://org.thoughtcrime.provider.securesms/capture";
  private static final Uri    PART_URI              = Uri.parse(PART_URI_STRING);
  private static final Uri    PERSISTENT_URI        = Uri.parse(PERSISTENT_URI_STRING);
  private static final int    PART_ROW              = 1;
  private static final int    PERSISTENT_ROW        = 2;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.thoughtcrime.provider.securesms", "part/*/#/*", PART_ROW);
    uriMatcher.addURI("org.thoughtcrime.provider.securesms", "capture/*/*/#/*", PERSISTENT_ROW);
  }

  @Override
  public boolean onCreate() {
    Log.w(TAG, "onCreate()");
    return true;
  }

  public static Uri getPublicContentUri(AttachmentId partId, String mimeType) {
    final Uri uri = Uri.withAppendedPath(PART_URI, String.valueOf(partId.getUniqueId()));
    return getPublicContentUri(ContentUris.withAppendedId(uri, partId.getRowId()), mimeType);
  }

  public static Uri getPublicContentUri(long persistentBlobId, String mimeType) {
    final Uri uri = PERSISTENT_URI.buildUpon()
                                  .appendPath(mimeType)
                                  .appendEncodedPath(String.valueOf(System.currentTimeMillis()))
                                  .build();
    return getPublicContentUri(ContentUris.withAppendedId(uri, persistentBlobId), mimeType);
  }

  private static Uri getPublicContentUri(Uri base, String mimeType) {
    final String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    final String filename  = "attachment." + (extension != null ? extension : "jpg");
    return base.buildUpon().appendEncodedPath(filename).build();
  }

  @SuppressWarnings("ConstantConditions")
  private File copyPartToTemporaryFile(InputStream in) throws IOException {
    File tmpDir           = getContext().getDir("tmp", 0);
    File tmpFile          = File.createTempFile("test", ".jpg", tmpDir);
    FileOutputStream fout = new FileOutputStream(tmpFile);

    byte[] buffer         = new byte[512];
    int read;

    while ((read = in.read(buffer)) != -1)
      fout.write(buffer, 0, read);

    in.close();

    return tmpFile;
  }

  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    MasterSecret masterSecret = KeyCachingService.getMasterSecret(getContext());
    Log.w(TAG, "openFile() called!");

    if (masterSecret == null) {
      Log.w(TAG, "masterSecret was null, abandoning.");
      return null;
    }

    int match = uriMatcher.match(uri);
    if (match != PART_ROW && match != PERSISTENT_ROW) {
      throw new FileNotFoundException("Request for bad part.");
    }

    try {
      InputStream in;
      switch (match) {
      case PART_ROW:
        Log.w(TAG, "Parting out a single row...");
        PartUriParser partUri = new PartUriParser(uri);
        in = DatabaseFactory.getAttachmentDatabase(getContext())
                            .getAttachmentStream(masterSecret, partUri.getPartId());
        break;
      default:
        PersistentBlobUriParser blobUri = new PersistentBlobUriParser(uri);
        in = PersistentBlobProvider.getInstance(getContext()).getStream(masterSecret, blobUri.getId());
      }

      File                 tmpFile = copyPartToTemporaryFile(in);
      ParcelFileDescriptor pdf     = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY);

      if (!tmpFile.delete()) {
        Log.w(TAG, "Failed to delete temp file.");
      }

      return pdf;
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      throw new FileNotFoundException("Error opening file");
    }

  }

  @Override
  public int delete(@NonNull Uri arg0, String arg1, String[] arg2) {
    return 0;
  }

  @Override
  public String getType(@NonNull Uri uri) {
    final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
    final String mimeType  = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    return MediaUtil.getCorrectedMimeType(mimeType);
  }

  @Override
  public Uri insert(@NonNull Uri arg0, ContentValues arg1) {
    return null;
  }

  @Override
  public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                      String[] selectionArgs, String sortOrder)
  {
    if (projection == null) {
      projection = new String[] {MediaColumns._ID, MediaColumns.DATA};
    }

    int match = uriMatcher.match(uri);
    if (match != PART_ROW && match != PERSISTENT_ROW) {
      return null;
    }

    PartUriParser           partUri = new PartUriParser(uri);
    PersistentBlobUriParser blobUri = new PersistentBlobUriParser(uri);
    MatrixCursor            cursor  = new MatrixCursor(projection);
    Object[]                row     = new Object[projection.length];

    for (int i = 0; i < row.length; i++) {
      switch (projection[i]) {
      case MediaColumns._ID:
        row[i] = (match == PART_ROW ? partUri.getPartId().getRowId() : blobUri.getId());
        break;
      case MediaColumns.DATA:
        row[i] = uri.toString();
        break;
      }
    }

    cursor.addRow(row);
    return cursor;
  }

  @Override
  public int update(@NonNull Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
    return 0;
  }
}
