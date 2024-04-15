package org.thoughtcrime.securesms.stickers;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.database.StickerTable.StickerRecordReader;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.emoji.EmojiSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class StickerSearchRepository {

  private final StickerTable    stickerDatabase;
  private final AttachmentTable attachmentDatabase;

  public StickerSearchRepository() {
    this.stickerDatabase    = SignalDatabase.stickers();
    this.attachmentDatabase = SignalDatabase.attachments();
  }

  public @NonNull Single<List<StickerRecord>> searchByEmoji(@NonNull String emoji) {
    if (emoji.isEmpty() || emoji.length() > EmojiSource.getLatest().getMaxEmojiLength()) {
      return Single.just(Collections.emptyList());
    }

    return Single.fromCallable(() -> searchByEmojiSync(emoji));
  }

  public void searchByEmoji(@NonNull String emoji, @NonNull Callback<List<StickerRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      callback.onResult(searchByEmojiSync(emoji));
    });
  }

  @WorkerThread
  private List<StickerRecord> searchByEmojiSync(@NonNull String emoji) {
    String              searchEmoji = EmojiUtil.getCanonicalRepresentation(emoji);
    List<StickerRecord> out         = new ArrayList<>();
    Set<String>         possible    = EmojiUtil.getAllRepresentations(searchEmoji);

    for (String candidate : possible) {
      try (StickerRecordReader reader = new StickerRecordReader(stickerDatabase.getStickersByEmoji(candidate))) {
        StickerRecord record = null;
        while ((record = reader.getNext()) != null) {
          out.add(record);
        }
      }
    }

    return out;
  }

  public void getStickerFeatureAvailability(@NonNull Callback<Boolean> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      callback.onResult(getStickerFeatureAvailabilitySync());
    });
  }

  @WorkerThread
  private Boolean getStickerFeatureAvailabilitySync() {
    try (Cursor cursor = stickerDatabase.getAllStickerPacks("1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return true;
      } else {
        return attachmentDatabase.hasStickerAttachments();
      }
    }
  }

  public interface Callback<T> {
    void onResult(T result);
  }
}
