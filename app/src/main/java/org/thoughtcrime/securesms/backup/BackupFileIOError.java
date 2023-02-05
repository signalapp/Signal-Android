package org.thoughtcrime.securesms.backup;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.PendingIntentFlags;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.notifications.NotificationCancellationHelper;
import org.thoughtcrime.securesms.notifications.NotificationChannels;

import java.io.IOException;

public enum BackupFileIOError {
  ACCESS_ERROR(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_your_backup_directory_has_been_deleted_or_moved),
  FILE_TOO_LARGE(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_your_backup_file_is_too_large),
  NOT_ENOUGH_SPACE(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_there_is_not_enough_space),
  VERIFICATION_FAILED(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_your_backup_could_not_be_verified),
  ATTACHMENT_TOO_LARGE(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_your_backup_contains_a_very_large_file),
  UNKNOWN(R.string.LocalBackupJobApi29_backup_failed, R.string.LocalBackupJobApi29_tap_to_manage_backups);

  private static final short BACKUP_FAILED_ID = 31321;

  private final @StringRes int titleId;
  private final @StringRes int messageId;

  BackupFileIOError(@StringRes int titleId, @StringRes int messageId) {
    this.titleId     = titleId;
    this.messageId   = messageId;
  }

  public static void clearNotification(@NonNull Context context) {
    NotificationCancellationHelper.cancelLegacy(context, BACKUP_FAILED_ID);
  }

  public void postNotification(@NonNull Context context) {
    PendingIntent pendingIntent           = PendingIntent.getActivity(context, -1, AppSettingsActivity.backups(context), PendingIntentFlags.mutable());
    Notification backupFailedNotification = new NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
                                                                  .setSmallIcon(R.drawable.ic_signal_backup)
                                                                  .setContentTitle(context.getString(titleId))
                                                                  .setContentText(context.getString(messageId))
                                                                  .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(messageId)))
                                                                  .setContentIntent(pendingIntent)
                                                                  .build();

    NotificationManagerCompat.from(context)
                             .notify(BACKUP_FAILED_ID, backupFailedNotification);
  }

  public static void postNotificationForException(@NonNull Context context, @NonNull IOException e) {
    BackupFileIOError error = getFromException(e);

    if (error != null) {
      error.postNotification(context);
    } else {
      UNKNOWN.postNotification(context);
    }
  }

  private static @Nullable BackupFileIOError getFromException(@NonNull IOException e) {
    if (e instanceof FullBackupExporter.InvalidBackupStreamException) {
      return ATTACHMENT_TOO_LARGE;
    } else if (e.getMessage() != null) {
      if (e.getMessage().contains("EFBIG")) {
        return FILE_TOO_LARGE;
      } else if (e.getMessage().contains("ENOSPC")) {
        return NOT_ENOUGH_SPACE;
      }
    }

    return null;
  }
}
