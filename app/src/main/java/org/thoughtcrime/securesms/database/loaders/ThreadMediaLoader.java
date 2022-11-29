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
    return createThreadMediaCursor(context, threadId, mediaType, sorting);
  }

  static Cursor createThreadMediaCursor(@NonNull Context context,
                                        long threadId,
                                        @NonNull MediaType mediaType,
                                        @NonNull MediaTable.Sorting sorting) {
    MediaTable mediaDatabase = SignalDatabase.media();

    switch (mediaType) {
      case GALLERY : return mediaDatabase.getGalleryMediaForThread(threadId, sorting);
      case DOCUMENT: return mediaDatabase.getDocumentMediaForThread(threadId, sorting);
      case AUDIO   : return mediaDatabase.getAudioMediaForThread(threadId, sorting);
      case ALL     : return mediaDatabase.getAllMediaForThread(threadId, sorting);
      default      : throw new AssertionError();
    }
  }

}
