package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.SignalDatabase;

public final class ThreadMediaLoader extends MediaLoader {

           private final long                  threadId;
  @NonNull private final MediaType          mediaType;
  @NonNull private final MediaTable.Sorting sorting;

  public ThreadMediaLoader(@NonNull Context context,
                           long threadId,
                           @NonNull MediaType mediaType,
                           @NonNull MediaTable.Sorting sorting)
  {
    super(context);
    this.threadId  = threadId;
    this.mediaType = mediaType;
    this.sorting   = sorting;
  }

  @Override
  public Cursor getCursor() {
    return createThreadMediaCursor(context, threadId, mediaType, sorting, 0);
  }

  static Cursor createThreadMediaCursor(@NonNull Context context,
                                        long threadId,
                                        @NonNull MediaType mediaType,
                                        @NonNull MediaTable.Sorting sorting,
                                        int limit) {
    MediaTable mediaDatabase = SignalDatabase.media();

    switch (mediaType) {
      case GALLERY : return mediaDatabase.getGalleryMediaForThread(threadId, sorting, limit);
      case DOCUMENT: return mediaDatabase.getDocumentMediaForThread(threadId, sorting, limit);
      case AUDIO   : return mediaDatabase.getAudioMediaForThread(threadId, sorting, limit);
      case LINK    : return mediaDatabase.getLinkMediaForThread(threadId, sorting);
      case ALL     : return mediaDatabase.getAllMediaForThread(threadId, sorting, limit);
      default      : throw new AssertionError();
    }
  }

}
