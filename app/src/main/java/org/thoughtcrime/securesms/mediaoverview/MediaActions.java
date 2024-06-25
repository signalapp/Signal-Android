package org.thoughtcrime.securesms.mediaoverview;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

final class MediaActions {

  private MediaActions() {
  }

  static void handleSaveMedia(@NonNull Fragment fragment,
                              @NonNull Collection<MediaTable.MediaRecord> mediaRecords,
                              @Nullable Runnable postExecute)
  {
    Context context = fragment.requireContext();

    if (StorageUtil.canWriteToMediaStore()) {
      performSaveToDisk(context, mediaRecords, postExecute);
      return;
    }

    SaveAttachmentTask.showWarningDialog(context, (dialogInterface, which) -> Permissions.with(fragment)
                      .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                      .ifNecessary()
                      .withPermanentDenialDialog(fragment.getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                      .onAnyDenied(() -> Toast.makeText(context, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                      .onAllGranted(() -> performSaveToDisk(context, mediaRecords, postExecute))
                      .execute(), mediaRecords.size());
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

          if (Recipient.self().getDeleteSyncCapability().isSupported() && Util.hasItems(deletedMessageRecords)) {
            MultiDeviceDeleteSyncJob.enqueueMessageDeletes(deletedMessageRecords);
          }

          return null;
        }

      }.execute(mediaRecords.toArray(new MediaTable.MediaRecord[0]))
    );
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private static void performSaveToDisk(@NonNull Context context, @NonNull Collection<MediaTable.MediaRecord> mediaRecords, @Nullable Runnable postExecute) {
    new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(context,
                                                                                 R.string.MediaOverviewActivity_collecting_attachments,
                                                                                 R.string.please_wait)
    {
      @Override
      protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
        List<SaveAttachmentTask.Attachment> attachments = new LinkedList<>();

        for (MediaTable.MediaRecord mediaRecord : mediaRecords) {
          if (mediaRecord.getAttachment().getUri() != null) {
            attachments.add(new SaveAttachmentTask.Attachment(mediaRecord.getAttachment().getUri(),
                                                              mediaRecord.getContentType(),
                                                              mediaRecord.getDate(),
                                                              mediaRecord.getAttachment().fileName));
          }
        }

        return attachments;
      }

      @Override
      protected void onPostExecute(List<SaveAttachmentTask.Attachment> attachments) {
        super.onPostExecute(attachments);
        SaveAttachmentTask saveTask = new SaveAttachmentTask(context, attachments.size());
        saveTask.executeOnExecutor(THREAD_POOL_EXECUTOR,
                                   attachments.toArray(new SaveAttachmentTask.Attachment[0]));

        if (postExecute != null) postExecute.run();
      }
    }.execute();
  }
}
