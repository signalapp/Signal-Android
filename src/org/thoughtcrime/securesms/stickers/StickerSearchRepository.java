package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.CursorList;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

public final class StickerSearchRepository {

  private final StickerDatabase    stickerDatabase;
  private final AttachmentDatabase attachmentDatabase;

  public StickerSearchRepository(@NonNull Context context) {
    this.stickerDatabase    = DatabaseFactory.getStickerDatabase(context);
    this.attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
  }

  public void searchByEmoji(@NonNull String emoji, @NonNull Callback<CursorList<StickerRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      Cursor cursor = stickerDatabase.getStickersByEmoji(emoji);

      if (cursor != null) {
        callback.onResult(new CursorList<>(cursor, new StickerModelBuilder()));
      } else {
        callback.onResult(CursorList.emptyList());
      }
    });
  }

  public void getStickerFeatureAvailability(@NonNull Callback<Boolean> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try (Cursor cursor = stickerDatabase.getAllStickerPacks("1")) {
        if (cursor != null && cursor.moveToFirst()) {
          callback.onResult(true);
        } else {
          callback.onResult(attachmentDatabase.hasStickerAttachments());
        }
      }
    });
  }

  private static class StickerModelBuilder implements CursorList.ModelBuilder<StickerRecord> {
    @Override
    public StickerRecord build(@NonNull Cursor cursor) {
      return new StickerDatabase.StickerRecordReader(cursor).getCurrent();
    }
  }

  private static class StickerPackModelBuilder implements CursorList.ModelBuilder<StickerPackRecord> {
    @Override
    public StickerPackRecord build(@NonNull Cursor cursor) {
      return new StickerDatabase.StickerPackRecordReader(cursor).getCurrent();
    }
  }

  public interface Callback<T> {
    void onResult(T result);
  }
}
