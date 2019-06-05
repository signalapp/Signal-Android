package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class MultiDeviceStickerPackOperationJob extends BaseJob implements InjectableType {

  private static final String TAG = Log.tag(MultiDeviceStickerPackOperationJob.class);

  public static final String KEY = "MultiDeviceStickerPackOperationJob";

  private static final String KEY_PACK_ID  = "pack_id";
  private static final String KEY_PACK_KEY = "pack_key";
  private static final String KEY_TYPE     = "type";

  private final String packId;
  private final String packKey;
  private final Type   type;

  @Inject SignalServiceMessageSender messageSender;

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
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_PACK_ID, packId)
                             .putString(KEY_PACK_KEY, packKey)
                             .putString(KEY_TYPE, type.name())
                             .build();
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

    StickerPackOperationMessage stickerPackOperation = new StickerPackOperationMessage(packIdBytes, packKeyBytes, remoteType);

    messageSender.sendMessage(SignalServiceSyncMessage.forStickerPackOperations(Collections.singletonList(stickerPackOperation)),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to sync sticker pack operation!");
  }

  // NEVER rename these -- they're persisted by name
  public enum Type {
    INSTALL, REMOVE
  }

  public static class Factory implements Job.Factory<MultiDeviceStickerPackOperationJob> {

    @Override
    public @NonNull MultiDeviceStickerPackOperationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceStickerPackOperationJob(parameters,
                                                    data.getString(KEY_PACK_ID),
                                                    data.getString(KEY_PACK_KEY),
                                                    Type.valueOf(data.getString(KEY_TYPE)));
    }
  }
}
