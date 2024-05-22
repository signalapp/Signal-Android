package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.database.model.IncomingSticker;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.signal.core.util.Hex;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class StickerDownloadJob extends BaseJob {

  public static final String KEY = "StickerDownloadJob";

  private static final String TAG = Log.tag(StickerDownloadJob.class);

  private static final String KEY_PACK_ID      = "pack_id";
  private static final String KEY_PACK_KEY     = "pack_key";
  private static final String KEY_PACK_TITLE   = "pack_title";
  private static final String KEY_PACK_AUTHOR  = "pack_author";
  private static final String KEY_STICKER_ID   = "sticker_id";
  private static final String KEY_EMOJI        = "emoji";
  private static final String KEY_CONTENT_TYPE = "content_type";
  private static final String KEY_COVER        = "cover";
  private static final String KEY_INSTALLED    = "installed";
  private static final String KEY_NOTIFY       = "notify";

  private final IncomingSticker sticker;
  private final boolean         notify;

  StickerDownloadJob(@NonNull IncomingSticker sticker, boolean notify) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .build(),
        sticker,
        notify);
  }

  private StickerDownloadJob(@NonNull Job.Parameters parameters, @NonNull IncomingSticker sticker, boolean notify) {
    super(parameters);
    this.sticker = sticker;
    this.notify  = notify;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_PACK_ID, sticker.getPackId())
                                    .putString(KEY_PACK_KEY, sticker.getPackKey())
                                    .putString(KEY_PACK_TITLE, sticker.getPackTitle())
                                    .putString(KEY_PACK_AUTHOR, sticker.getPackAuthor())
                                    .putInt(KEY_STICKER_ID, sticker.getStickerId())
                                    .putString(KEY_EMOJI, sticker.getEmoji())
                                    .putString(KEY_CONTENT_TYPE, sticker.getContentType())
                                    .putBoolean(KEY_COVER, sticker.isCover())
                                    .putBoolean(KEY_INSTALLED, sticker.isInstalled())
                                    .putBoolean(KEY_NOTIFY, notify)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    StickerTable db = SignalDatabase.stickers();

    StickerRecord stickerRecord = db.getSticker(sticker.getPackId(), sticker.getStickerId(), sticker.isCover());
    if (stickerRecord != null) {
      try (InputStream stream = PartAuthority.getAttachmentStream(context, stickerRecord.getUri())) {
        if (stream != null) {
          Log.w(TAG, "Sticker already downloaded.");
          return;
        }
      } catch (FileNotFoundException e) {
        Log.w(TAG, "Sticker file no longer exists, downloading again.");
      }
    }

    if (!db.isPackInstalled(sticker.getPackId()) && !sticker.isCover()) {
      Log.w(TAG, "Pack is no longer installed.");
      return;
    }

    SignalServiceMessageReceiver receiver     = AppDependencies.getSignalServiceMessageReceiver();
    byte[]                       packIdBytes  = Hex.fromStringCondensed(sticker.getPackId ());
    byte[]                       packKeyBytes = Hex.fromStringCondensed(sticker.getPackKey());
    InputStream                  stream       = receiver.retrieveSticker(packIdBytes, packKeyBytes, sticker.getStickerId());

    db.insertSticker(sticker, stream, notify);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to download sticker!");
  }

  public static final class Factory implements Job.Factory<StickerDownloadJob> {
    @Override
    public @NonNull StickerDownloadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      IncomingSticker sticker = new IncomingSticker(data.getString(KEY_PACK_ID),
                                                    data.getString(KEY_PACK_KEY),
                                                    data.getString(KEY_PACK_TITLE),
                                                    data.getString(KEY_PACK_AUTHOR),
                                                    data.getInt(KEY_STICKER_ID),
                                                    data.getString(KEY_EMOJI),
                                                    data.getString(KEY_CONTENT_TYPE),
                                                    data.getBoolean(KEY_COVER),
                                                    data.getBoolean(KEY_INSTALLED));

      return new StickerDownloadJob(parameters, sticker, data.getBoolean(KEY_NOTIFY));
    }
  }
}
