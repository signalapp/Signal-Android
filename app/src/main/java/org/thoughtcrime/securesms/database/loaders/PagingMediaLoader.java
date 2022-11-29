package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.MediaTable.Sorting;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.AsyncLoader;

public final class PagingMediaLoader extends AsyncLoader<Pair<Cursor, Integer>> {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(PagingMediaLoader.class);

  private final Uri                       uri;
  private final boolean                   leftIsRecent;
  private final Sorting                   sorting;
  private final long                      threadId;
  private final DatabaseObserver.Observer observer;

  public PagingMediaLoader(@NonNull Context context, long threadId, @NonNull Uri uri, boolean leftIsRecent, @NonNull Sorting sorting) {
    super(context);
    this.threadId     = threadId;
    this.uri          = uri;
    this.leftIsRecent = leftIsRecent;
    this.sorting      = sorting;
    this.observer     = () -> {
      ThreadUtil.runOnMain(this::onContentChanged);
    };
  }

  @Override
  public @Nullable Pair<Cursor, Integer> loadInBackground() {
    ApplicationDependencies.getDatabaseObserver().registerAttachmentObserver(observer);

    Cursor cursor = SignalDatabase.media().getGalleryMediaForThread(threadId, sorting);

    while (cursor.moveToNext()) {
      AttachmentId attachmentId  = new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.ROW_ID)), cursor.getLong(cursor.getColumnIndexOrThrow(AttachmentTable.UNIQUE_ID)));
      Uri          attachmentUri = PartAuthority.getAttachmentDataUri(attachmentId);

      if (attachmentUri.equals(uri)) {
        return new Pair<>(cursor, leftIsRecent ? cursor.getPosition() : cursor.getCount() - 1 - cursor.getPosition());
      }
    }

    return null;
  }

  @Override
  protected void onAbandon() {
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);
  }
}
