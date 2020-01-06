package org.thoughtcrime.securesms.mediaoverview;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

final class MediaActions {

  private MediaActions() {
  }

  static void handleSaveMedia(@NonNull Fragment fragment,
                              @NonNull Collection<MediaDatabase.MediaRecord> mediaRecords,
                              @Nullable Runnable postExecute)
  {
    Context context = fragment.requireContext();

    SaveAttachmentTask.showWarningDialog(context, (dialogInterface, which) -> Permissions.with(fragment)
                      .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                      .ifNecessary()
                      .withPermanentDenialDialog(fragment.getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                      .onAnyDenied(() -> Toast.makeText(context, R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                      .onAllGranted(() ->
                        new ProgressDialogAsyncTask<Void, Void, List<SaveAttachmentTask.Attachment>>(context,
                                                                                                     R.string.MediaOverviewActivity_collecting_attachments,
                                                                                                     R.string.please_wait)
                        {
                          @Override
                          protected List<SaveAttachmentTask.Attachment> doInBackground(Void... params) {
                            List<SaveAttachmentTask.Attachment> attachments = new LinkedList<>();

                            for (MediaDatabase.MediaRecord mediaRecord : mediaRecords) {
                              if (mediaRecord.getAttachment().getDataUri() != null) {
                                attachments.add(new SaveAttachmentTask.Attachment(mediaRecord.getAttachment().getDataUri(),
                                                                                  mediaRecord.getContentType(),
                                                                                  mediaRecord.getDate(),
                                                                                  mediaRecord.getAttachment().getFileName()));
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
                        }.execute()
                      ).execute(), mediaRecords.size());
  }

  static void handleDeleteMedia(@NonNull Context context,
                                @NonNull Collection<MediaDatabase.MediaRecord> mediaRecords)
  {
    int       recordCount    = mediaRecords.size();
    Resources res            = context.getResources();
    String    confirmTitle   = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_title,
                                                     recordCount,
                                                     recordCount);
    String    confirmMessage = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_message,
                                                     recordCount,
                                                     recordCount);

    AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                                 .setIconAttribute(R.attr.dialog_alert_icon)
                                                 .setTitle(confirmTitle)
                                                 .setMessage(confirmMessage)
                                                 .setCancelable(true);

    builder.setPositiveButton(R.string.delete, (dialogInterface, i) ->
      new ProgressDialogAsyncTask<MediaDatabase.MediaRecord, Void, Void>(context,
                                                                         R.string.MediaOverviewActivity_Media_delete_progress_title,
                                                                         R.string.MediaOverviewActivity_Media_delete_progress_message)
      {
        @Override
        protected Void doInBackground(MediaDatabase.MediaRecord... records) {
          if (records == null || records.length == 0) {
            return null;
          }

          for (MediaDatabase.MediaRecord record : records) {
            AttachmentUtil.deleteAttachment(context, record.getAttachment());
          }
          return null;
        }

      }.execute(mediaRecords.toArray(new MediaDatabase.MediaRecord[0]))
    );
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }
}
