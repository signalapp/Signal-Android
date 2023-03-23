package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord;

/**
 * Check for unknown fields stored on self and attempt to apply them.
 */
public class ApplyUnknownFieldsToSelfMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(ApplyUnknownFieldsToSelfMigrationJob.class);

  public static final String KEY = "ApplyUnknownFieldsToSelfMigrationJob";

  ApplyUnknownFieldsToSelfMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private ApplyUnknownFieldsToSelfMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getAci() == null) {
      Log.w(TAG, "Not registered!");
      return;
    }

    Recipient       self;
    RecipientRecord settings;

    try {
      self     = Recipient.self();
      settings = SignalDatabase.recipients().getRecordForSync(self.getId());
    } catch (RecipientTable.MissingRecipientException e) {
      Log.w(TAG, "Unable to find self");
      return;
    }

    if (settings == null || settings.getSyncExtras().getStorageProto() == null) {
      Log.d(TAG, "No unknowns to apply");
      return;
    }

    try {
      StorageId           storageId           = StorageId.forAccount(self.getStorageServiceId());
      AccountRecord       accountRecord       = AccountRecord.parseFrom(settings.getSyncExtras().getStorageProto());
      SignalAccountRecord signalAccountRecord = new SignalAccountRecord(storageId, accountRecord);

      Log.d(TAG, "Applying potentially now known unknowns");
      StorageSyncHelper.applyAccountStorageSyncUpdates(context, self, signalAccountRecord, false);
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<ApplyUnknownFieldsToSelfMigrationJob> {
    @Override
    public @NonNull ApplyUnknownFieldsToSelfMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ApplyUnknownFieldsToSelfMigrationJob(parameters);
    }
  }
}
