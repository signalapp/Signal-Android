package org.thoughtcrime.securesms.mediaoverview;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentSaver;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.rxjava3.core.Completable;

final class MediaActions {

  private MediaActions() {
  }

  static Completable handleSaveMedia(@NonNull Fragment fragment,
                                     @NonNull Collection<MediaTable.MediaRecord> mediaRecords)
  {
    return new AttachmentSaver(fragment).saveAttachmentsRx(mediaRecords);
  }

  static void handleDeleteMedia(@NonNull Context context,
                                @NonNull Collection<MediaTable.MediaRecord> mediaRecords)
  {
    int       recordCount    = mediaRecords.size();
    Resources res            = context.getResources();
    String    confirmTitle   = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_title, recordCount, recordCount);
    String    confirmMessage = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_message, recordCount, recordCount);

    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setTitle(confirmTitle)
                                                                                .setMessage(confirmMessage)
                                                                                .setCancelable(true);

    builder.setPositiveButton(R.string.delete, (dialogInterface, i) ->
      new ProgressDialogAsyncTask<MediaTable.MediaRecord, Void, Void>(context,
                                                                      R.string.MediaOverviewActivity_Media_delete_progress_title,
                                                                      R.string.MediaOverviewActivity_Media_delete_progress_message)
      {
        @Override
        protected Void doInBackground(MediaTable.MediaRecord... records) {
          if (records == null || records.length == 0) {
            return null;
          }

          Set<MessageRecord> deletedMessageRecords = new HashSet<>(records.length);
          for (MediaTable.MediaRecord record : records) {
            MessageRecord deleted = AttachmentUtil.deleteAttachment(record.getAttachment());
            if (deleted != null) {
              deletedMessageRecords.add(deleted);
            }
          }

          if (Util.hasItems(deletedMessageRecords)) {
            MultiDeviceDeleteSyncJob.enqueueMessageDeletes(deletedMessageRecords);
          }

          return null;
        }

      }.execute(mediaRecords.toArray(new MediaTable.MediaRecord[0]))
    );
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }
}
