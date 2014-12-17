package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.AsymmetricMasterCipher;
import org.thoughtcrime.securesms.crypto.AsymmetricMasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.SmsCipher;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;

public class ThumbnailGenerateJob extends MasterSecretJob {

  private static final String TAG = ThumbnailGenerateJob.class.getSimpleName();

  private final long partId;

  public ThumbnailGenerateJob(Context context, long partId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.partId = partId;
  }

  @Override
  public void onAdded() { }

  @Override
  public void onRun(MasterSecret masterSecret) throws MmsException {
    PartDatabase database = DatabaseFactory.getPartDatabase(context);
    PduPart part = database.getPart(partId);

    if (part.getThumbnailUri() != null) {
      return;
    }

    long startMillis = System.currentTimeMillis();
    Bitmap thumbnail = generateThumbnailForPart(masterSecret, part);

    if (thumbnail != null) {
      ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
      thumbnail.compress(CompressFormat.JPEG, 85, thumbnailBytes);

      float aspectRatio = (float)thumbnail.getWidth() / (float)thumbnail.getHeight();
      Log.w(TAG, String.format("generated thumbnail for part #%d, %dx%d (%.3f:1) in %dms",
                               partId,
                               thumbnail.getWidth(),
                               thumbnail.getHeight(),
                               aspectRatio, System.currentTimeMillis() - startMillis));
      database.updatePartThumbnail(masterSecret, partId, part, new ByteArrayInputStream(thumbnailBytes.toByteArray()), aspectRatio);
    } else {
      Log.w(TAG, "thumbnail not generated");
    }
  }

  private Bitmap generateThumbnailForPart(MasterSecret masterSecret, PduPart part) {
    String contentType = new String(part.getContentType());

    if      (ContentType.isImageType(contentType)) return generateImageThumbnail(masterSecret, part);
    else                                           return null;
  }

  private Bitmap generateImageThumbnail(MasterSecret masterSecret, PduPart part) {
    try {
      int maxSize = context.getResources().getDimensionPixelSize(R.dimen.thumbnail_max_size);
      return BitmapUtil.createScaledBitmap(context, masterSecret, part.getDataUri(), maxSize, maxSize);
    } catch (FileNotFoundException | BitmapDecodingException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() { }
}
