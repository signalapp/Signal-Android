package org.thoughtcrime.securesms.devicetransfer.newdevice;

import android.content.Context;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.ServerTask;
import org.thoughtcrime.securesms.AppInitialization;
import org.thoughtcrime.securesms.backup.BackupEvent;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.backup.FullBackupImporter;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.notifications.NotificationChannels;

import java.io.IOException;
import java.io.InputStream;

/**
 * Performs the restore with the backup data coming in over the input stream. Used in
 * conjunction with {@link org.signal.devicetransfer.DeviceToDeviceTransferService}.
 */
final class NewDeviceServerTask implements ServerTask {

  private static final String TAG = Log.tag(NewDeviceServerTask.class);

  @Override
  public void run(@NonNull Context context, @NonNull InputStream inputStream) {
    long start = System.currentTimeMillis();

    Log.i(TAG, "Starting backup restore.");

    EventBus.getDefault().register(this);
    try {
      SQLiteDatabase database = SignalDatabase.getBackupDatabase();

      String passphrase = "deadbeef";

      BackupPassphrase.set(context, passphrase);
      FullBackupImporter.importFile(context,
                                    AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret(),
                                    database,
                                    inputStream,
                                    passphrase);

      SignalDatabase.runPostBackupRestoreTasks(database);
      NotificationChannels.getInstance().restoreContactNotificationChannels();

      AppInitialization.onPostBackupRestore(context);

      Log.i(TAG, "Backup restore complete.");
    } catch (FullBackupImporter.DatabaseDowngradeException e) {
      Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e);
      EventBus.getDefault().post(new Status(0, Status.State.FAILURE_VERSION_DOWNGRADE));
    } catch (IOException e) {
      Log.w(TAG, e);
      EventBus.getDefault().post(new Status(0, Status.State.FAILURE_UNKNOWN));
    } finally {
      EventBus.getDefault().unregister(this);
    }

    long end = System.currentTimeMillis();
    Log.i(TAG, "Receive took: " + (end - start));
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void onEvent(BackupEvent event) {
    if (event.getType() == BackupEvent.Type.PROGRESS) {
      EventBus.getDefault().post(new Status(event.getCount(), Status.State.IN_PROGRESS));
    } else if (event.getType() == BackupEvent.Type.FINISHED) {
      EventBus.getDefault().post(new Status(event.getCount(), Status.State.SUCCESS));
    }
  }

  public static final class Status {
    private final long  messageCount;
    private final State state;

    public Status(long messageCount, State state) {
      this.messageCount = messageCount;
      this.state        = state;
    }

    public long getMessageCount() {
      return messageCount;
    }

    public @NonNull State getState() {
      return state;
    }

    public enum State {
      IN_PROGRESS,
      SUCCESS,
      FAILURE_VERSION_DOWNGRADE,
      FAILURE_UNKNOWN
    }
  }
}
