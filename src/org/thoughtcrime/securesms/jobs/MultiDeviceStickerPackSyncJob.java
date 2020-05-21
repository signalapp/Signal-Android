package org.thoughtcrime.securesms.jobs;

import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Tells a linked desktop about all installed sticker packs.
 */
public class MultiDeviceStickerPackSyncJob extends BaseJob implements InjectableType {

  private static final String TAG = Log.tag(MultiDeviceStickerPackSyncJob.class);

  public static final String KEY = "MultiDeviceStickerPackSyncJob";

  @Inject SignalServiceMessageSender messageSender;

  public MultiDeviceStickerPackSyncJob() {
    this(new Parameters.Builder()
                           .setQueue("MultiDeviceStickerPackSyncJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .build());
  }

  public MultiDeviceStickerPackSyncJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    return;
    /*
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    List<StickerPackOperationMessage> operations = new LinkedList<>();

    try (StickerPackRecordReader reader = new StickerPackRecordReader(DatabaseFactory.getStickerDatabase(context).getInstalledStickerPacks())) {
      StickerPackRecord pack;
      while ((pack = reader.getNext()) != null) {
        byte[] packIdBytes  = Hex.fromStringCondensed(pack.getPackId());
        byte[] packKeyBytes = Hex.fromStringCondensed(pack.getPackKey());

        operations.add(new StickerPackOperationMessage(packIdBytes, packKeyBytes, StickerPackOperationMessage.Type.INSTALL));
      }
    }

    messageSender.sendMessage(SignalServiceSyncMessage.forStickerPackOperations(operations),
                              UnidentifiedAccessUtil.getAccessForSync(context));
     */
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to sync sticker pack operation!");
  }

  public static class Factory implements Job.Factory<MultiDeviceStickerPackSyncJob> {

    @Override
    public @NonNull
    MultiDeviceStickerPackSyncJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceStickerPackSyncJob(parameters);
    }
  }
}
