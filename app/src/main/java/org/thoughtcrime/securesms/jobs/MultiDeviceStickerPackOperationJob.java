package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Hex;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MultiDeviceStickerPackOperationJob extends BaseJob {

  private static final String TAG = Log.tag(MultiDeviceStickerPackOperationJob.class);

  public static final String KEY = "MultiDeviceStickerPackOperationJob";

  private static final String KEY_PACK_ID  = "pack_id";
  private static final String KEY_PACK_KEY = "pack_key";
  private static final String KEY_TYPE     = "type";

  private final String packId;
  private final String packKey;
  private final Type   type;

  public MultiDeviceStickerPackOperationJob(@NonNull String packId,
                                            @NonNull String packKey,
                                            @NonNull Type type)
  {
    this(new Job.Parameters.Builder()
                           .setQueue("MultiDeviceStickerPackOperationJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .build(),
        packId,
        packKey,
        type);
  }

  public MultiDeviceStickerPackOperationJob(@NonNull Parameters parameters,
                                            @NonNull String packId,
                                            @NonNull String packKey,
                                            @NonNull Type type)
  {
    super(parameters);
    this.packId  = packId;
    this.packKey = packKey;
    this.type    = type;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_PACK_ID, packId)
                                    .putString(KEY_PACK_KEY, packKey)
                                    .putString(KEY_TYPE, type.name())
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    byte[] packIdBytes  = Hex.fromStringCondensed(packId);
    byte[] packKeyBytes = Hex.fromStringCondensed(packKey);

    StickerPackOperationMessage.Type remoteType;

    switch (type) {
      case INSTALL: remoteType = StickerPackOperationMessage.Type.INSTALL; break;
      case REMOVE:  remoteType = StickerPackOperationMessage.Type.REMOVE; break;
      default:      throw new AssertionError("No matching type?");
    }

    SignalServiceMessageSender  messageSender        = AppDependencies.getSignalServiceMessageSender();
    StickerPackOperationMessage stickerPackOperation = new StickerPackOperationMessage(packIdBytes, packKeyBytes, remoteType);

    messageSender.sendSyncMessage(SignalServiceSyncMessage.forStickerPackOperations(Collections.singletonList(stickerPackOperation))
    );
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to sync sticker pack operation!");
  }

  // NEVER rename these -- they're persisted by name
  public enum Type {
    INSTALL, REMOVE
  }

  public static class Factory implements Job.Factory<MultiDeviceStickerPackOperationJob> {

    @Override
    public @NonNull MultiDeviceStickerPackOperationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new MultiDeviceStickerPackOperationJob(parameters,
                                                    data.getString(KEY_PACK_ID),
                                                    data.getString(KEY_PACK_KEY),
                                                    Type.valueOf(data.getString(KEY_TYPE)));
    }
  }
}
