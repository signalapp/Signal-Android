package org.thoughtcrime.securesms.mediapreview;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.MediaDatabase.MediaRecord;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class MediaPreviewViewModel extends ViewModel {

  private final MutableLiveData<PreviewData> previewData = new MutableLiveData<>();

  private boolean leftIsRecent;

  private @Nullable Cursor cursor;

  public void setCursor(@Nullable Cursor cursor, boolean leftIsRecent) {
    this.cursor       = cursor;
    this.leftIsRecent = leftIsRecent;
  }

  public void setActiveAlbumRailItem(@NonNull Context context, int activePosition) {
    if (cursor == null) {
      previewData.postValue(new PreviewData(Collections.emptyList(), null, 0));
      return;
    }

    activePosition = getCursorPosition(activePosition);

    cursor.moveToPosition(activePosition);

    MediaRecord             activeRecord = MediaRecord.from(context, cursor);
    LinkedList<MediaRecord> rail         = new LinkedList<>();

    rail.add(activeRecord);

    while (cursor.moveToPrevious()) {
      MediaRecord record = MediaRecord.from(context, cursor);
      if (record.getAttachment().getMmsId() == activeRecord.getAttachment().getMmsId()) {
        rail.addFirst(record);
      } else {
        break;
      }
    }

    cursor.moveToPosition(activePosition);

    while (cursor.moveToNext()) {
      MediaRecord record = MediaRecord.from(context, cursor);
      if (record.getAttachment().getMmsId() == activeRecord.getAttachment().getMmsId()) {
        rail.addLast(record);
      } else {
        break;
      }
    }

    if (!leftIsRecent) {
      Collections.reverse(rail);
    }

    previewData.postValue(new PreviewData(rail.size() > 1 ? rail : Collections.emptyList(),
                                          activeRecord.getAttachment().getCaption(),
                                          rail.indexOf(activeRecord)));
  }

  private int getCursorPosition(int position) {
    if (cursor == null) {
      return 0;
    }

    if (leftIsRecent) return position;
    else              return cursor.getCount() - 1 - position;
  }

  public LiveData<PreviewData> getPreviewData() {
    return previewData;
  }

  public static class PreviewData {
    private final List<MediaRecord> albumThumbnails;
    private final String            caption;
    private final int               activePosition;

    public PreviewData(@NonNull List<MediaRecord> albumThumbnails, @Nullable String caption, int activePosition) {
      this.albumThumbnails = albumThumbnails;
      this.caption         = caption;
      this.activePosition  = activePosition;
    }

    public @NonNull List<MediaRecord> getAlbumThumbnails() {
      return albumThumbnails;
    }

    public @Nullable String getCaption() {
      return caption;
    }

    public int getActivePosition() {
      return activePosition;
    }
  }
}
