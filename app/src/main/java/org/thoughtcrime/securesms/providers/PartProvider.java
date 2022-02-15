/*
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

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.MemoryFileUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class PartProvider extends BaseContentProvider {

  private static final String TAG = Log.tag(PartProvider.class);

  private static final String CONTENT_AUTHORITY  = BuildConfig.APPLICATION_ID + ".part";
  private static final String CONTENT_URI_STRING = "content://" + CONTENT_AUTHORITY + "/part";
  private static final Uri    CONTENT_URI        = Uri.parse(CONTENT_URI_STRING);
  private static final int    SINGLE_ROW         = 1;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI(CONTENT_AUTHORITY, "part/*/#", SINGLE_ROW);
  }

  @Override
  public boolean onCreate() {
    Log.i(TAG, "onCreate()");
    return true;
  }

  public static Uri getContentUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
    Log.i(TAG, "openFile() called!");

    if (KeyCachingService.isLocked(getContext())) {
      Log.w(TAG, "masterSecret was null, abandoning.");
      return null;
    }

    if (uriMatcher.match(uri) == SINGLE_ROW) {
      Log.i(TAG, "Parting out a single row...");
      try {
        final PartUriParser partUri = new PartUriParser(uri);
        return getParcelStreamForAttachment(partUri.getPartId());
      } catch (IOException ioe) {
        Log.w(TAG, ioe);
        throw new FileNotFoundException("Error opening file");
      }
    }

    throw new FileNotFoundException("Request for bad part.");
  }

  @Override
  public int delete(@NonNull Uri arg0, String arg1, String[] arg2) {
    Log.i(TAG, "delete() called");
    return 0;
  }

  @Override
  public String getType(@NonNull Uri uri) {
    Log.i(TAG, "getType() called: " + uri);

    if (uriMatcher.match(uri) == SINGLE_ROW) {
      PartUriParser      partUriParser = new PartUriParser(uri);
      DatabaseAttachment attachment    = SignalDatabase.attachments().getAttachment(partUriParser.getPartId());

      if (attachment != null) {
        Log.i(TAG, "getType() called: " + uri + " It's " + attachment.getContentType());
        return attachment.getContentType();
      }
    }

    return null;
  }

  @Override
  public Uri insert(@NonNull Uri arg0, ContentValues arg1) {
    Log.i(TAG, "insert() called");
    return null;
  }

  @Override
  public Cursor query(@NonNull Uri url, @Nullable String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    Log.i(TAG, "query() called: " + url);

    if (uriMatcher.match(url) == SINGLE_ROW) {
      PartUriParser      partUri    = new PartUriParser(url);
      DatabaseAttachment attachment = SignalDatabase.attachments().getAttachment(partUri.getPartId());

      if (attachment == null) return null;

      long fileSize = attachment.getSize();

      if (fileSize <= 0) {
        Log.w(TAG, "Empty file " + fileSize);
        return null;
      }

      String fileName = attachment.getFileName() != null ? attachment.getFileName()
                                                         : createFileNameForMimeType(attachment.getContentType());

      return createCursor(projection, fileName, fileSize);
    } else {
      return null;
    }
  }

  @Override
  public int update(@NonNull Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
    Log.i(TAG, "update() called");
    return 0;
  }

  private ParcelFileDescriptor getParcelStreamForAttachment(AttachmentId attachmentId) throws IOException {
    long       plaintextLength = StreamUtil.getStreamLength(SignalDatabase.attachments().getAttachmentStream(attachmentId, 0));
    MemoryFile memoryFile      = new MemoryFile(attachmentId.toString(), Util.toIntExact(plaintextLength));

    InputStream  in  = SignalDatabase.attachments().getAttachmentStream(attachmentId, 0);
    OutputStream out = memoryFile.getOutputStream();

    StreamUtil.copy(in, out);
    StreamUtil.close(out);
    StreamUtil.close(in);

    return MemoryFileUtil.getParcelFileDescriptor(memoryFile);
  }
}
