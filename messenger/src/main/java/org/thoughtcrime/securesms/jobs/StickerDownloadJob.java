package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.model.IncomingSticker;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class StickerDownloadJob extends BaseJob implements InjectableType {

  public static final String KEY = "StickerDownloadJob";

  private static final String TAG = Log.tag(StickerDownloadJob.class);

  private static final String KEY_PACK_ID     = "pack_id";
  private static final String KEY_PACK_KEY    = "pack_key";
  private static final String KEY_PACK_TITLE  = "pack_title";
  private static final String KEY_PACK_AUTHOR = "pack_author";
  private static final String KEY_STICKER_ID  = "sticker_id";
  private static final String KEY_EMOJI       = "emoji";
  private static final String KEY_COVER       = "cover";
  private static final String KEY_INSTALLED   = "installed";

  private final IncomingSticker sticker;

  @Inject SignalServiceMessageReceiver receiver;

  public StickerDownloadJob(@NonNull IncomingSticker sticker) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .build(),
        sticker);
  }

  private StickerDownloadJob(@NonNull Job.Parameters parameters, @NonNull IncomingSticker sticker) {
    super(parameters);
    this.sticker = sticker;
  }

  @Override
  protected void onRun() throws Exception {
    StickerDatabase db = DatabaseFactory.getStickerDatabase(context);

    if (db.getSticker(sticker.getPackId(), sticker.getStickerId(), sticker.isCover()) != null) {
      Log.w(TAG, "Sticker already downloaded.");
      return;
    }

    if (!db.isPackInstalled(sticker.getPackId()) && !sticker.isCover()) {
      Log.w(TAG, "Pack is no longer installed.");
      return;
    }

    byte[]      packIdBytes  = Hex.fromStringCondensed(sticker.getPackId());
    byte[]      packKeyBytes = Hex.fromStringCondensed(sticker.getPackKey());
    InputStream stream       = receiver.retrieveSticker(packIdBytes, packKeyBytes, sticker.getStickerId());

    db.insertSticker(sticker, stream);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_PACK_ID, sticker.getPackId())
                             .putString(KEY_PACK_KEY, sticker.getPackKey())
                             .putString(KEY_PACK_TITLE, sticker.getPackTitle())
                             .putString(KEY_PACK_AUTHOR, sticker.getPackAuthor())
                             .putInt(KEY_STICKER_ID, sticker.getStickerId())
                             .putString(KEY_EMOJI, sticker.getEmoji())
                             .putBoolean(KEY_COVER, sticker.isCover())
                             .putBoolean(KEY_INSTALLED, sticker.isInstalled())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to download sticker!");
  }

  public static final class Factory implements Job.Factory<StickerDownloadJob> {
    @Override
    public @NonNull StickerDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      IncomingSticker sticker = new IncomingSticker(data.getString(KEY_PACK_ID),
                                                    data.getString(KEY_PACK_KEY),
                                                    data.getString(KEY_PACK_TITLE),
                                                    data.getString(KEY_PACK_AUTHOR),
                                                    data.getInt(KEY_STICKER_ID),
                                                    data.getString(KEY_EMOJI),
                                                    data.getBoolean(KEY_COVER),
                                                    data.getBoolean(KEY_INSTALLED));

      return new StickerDownloadJob(parameters, sticker);
    }
  }
}
