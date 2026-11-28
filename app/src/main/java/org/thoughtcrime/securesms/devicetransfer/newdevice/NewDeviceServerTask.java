package org.thoughtcrime.securesms.devicetransfer.newdevice;

import android.content.Context;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.crypto.AttachmentSecretProvider;
import org.signal.core.util.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.NewDeviceRestoreStatus;
import org.signal.devicetransfer.ServerTask;
import org.thoughtcrime.securesms.AppInitialization;
import org.thoughtcrime.securesms.backup.BackupEvent;
import org.thoughtcrime.securesms.backup.FullBackupImporter;
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.impl.DataRestoreConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;

import java.io.IOException;
import java.io.InputStream;

/**
 * Performs the restore with the backup data coming in over the input stream. Used in
 * conjunction with {@link org.signal.devicetransfer.DeviceToDeviceTransferService}.
 */
public final class NewDeviceServerTask implements ServerTask {

  public NewDeviceServerTask() {}


  private static final String TAG = Log.tag(NewDeviceServerTask.class);

  @Override
  public void run(@NonNull Context context, @NonNull InputStream inputStream) {
    long start = System.currentTimeMillis();

    Log.i(TAG, "Starting backup restore.");

    EventBus.Subscription subscription = EventBus.getDefault().subscribe(BackupEvent.class, this::onBackupEvent);
    try {
      DataRestoreConstraint.setRestoringData(true);
      SQLiteDatabase database = SignalDatabase.getBackupDatabase();

      String passphrase = SignalStore.account().getAccountEntropyPool().getValue();

      SignalStore.backup().setV1BackupPassphrase(passphrase);
      FullBackupImporter.importFile(context,
                                    AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore.INSTANCE).getOrCreateAttachmentSecret(),
                                    database,
                                    inputStream,
                                    passphrase,
                                    true);

      SignalDatabase.runPostBackupRestoreTasks(database);
      NotificationChannels.getInstance().restoreContactNotificationChannels();

      AppInitialization.onPostBackupRestore(context);

      Log.i(TAG, "Backup restore complete.");
    } catch (FullBackupImporter.DatabaseDowngradeException e) {
      Log.w(TAG, "Failed due to the backup being from a newer version of Signal.", e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_VERSION_DOWNGRADE));
    } catch (FullBackupImporter.ForeignKeyViolationException e) {
      Log.w(TAG, "Failed due to foreign key constraint violations.", e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_FOREIGN_KEY));
    } catch (IOException e) {
      Log.w(TAG, e);
      EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.FAILURE_UNKNOWN));
    } finally {
      subscription.unsubscribe();
      DataRestoreConstraint.setRestoringData(false);
    }

    long end = System.currentTimeMillis();
    Log.i(TAG, "Receive took: " + (end - start));

    EventBus.getDefault().post(new NewDeviceRestoreStatus(0, NewDeviceRestoreStatus.State.RESTORE_COMPLETE));
  }

  private void onBackupEvent(@NonNull BackupEvent event) {
    if (event.getType() == BackupEvent.Type.PROGRESS) {
      EventBus.getDefault().post(new NewDeviceRestoreStatus(event.getCount(), NewDeviceRestoreStatus.State.IN_PROGRESS));
    } else if (event.getType() == BackupEvent.Type.FINISHED) {
      EventBus.getDefault().post(new NewDeviceRestoreStatus(event.getCount(), NewDeviceRestoreStatus.State.TRANSFER_COMPLETE));
    }
  }

}
