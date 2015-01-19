package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.TextSecureExpiredException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendReq;

public abstract class SendJob extends MasterSecretJob {
  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  @Override
  public final void onRun(MasterSecret masterSecret) throws Exception {
    if (!Util.isBuildFresh()) {
      throw new TextSecureExpiredException(String.format("TextSecure expired (build %d, now %d)",
                                                         BuildConfig.BUILD_TIMESTAMP,
                                                         System.currentTimeMillis()));
    }

    onSend(masterSecret);
  }

  protected abstract void onSend(MasterSecret masterSecret) throws Exception;

  protected void prepareMessageMedia(MasterSecret masterSecret, SendReq message,
                                     MediaConstraints constraints, boolean toMemory)
      throws IOException, UndeliverableMessageException
  {
    try {
      for (int i = 0; i < message.getBody().getPartsNum(); i++) {
        preparePart(masterSecret, constraints, message.getBody().getPart(i), toMemory);
      }
    } catch (MmsException me) {
      throw new UndeliverableMessageException(me);
    }
  }

  private void preparePart(MasterSecret masterSecret, MediaConstraints constraints,
                           PduPart part, boolean toMemory)
      throws IOException, MmsException, UndeliverableMessageException
  {
    byte[] resizedData = null;

    if (!constraints.isSatisfied(context, masterSecret, part)) {
      if (!constraints.canResize(part)) {
        throw new UndeliverableMessageException("Size constraints could not be satisfied.");
      }
      resizedData = resizePart(masterSecret, constraints, part);
    }

    if (toMemory && part.getDataUri() != null) {
      part.setData(resizedData != null ? resizedData : MediaUtil.getPartData(context, masterSecret, part));
    }

    if (resizedData != null) {
      part.setDataSize(resizedData.length);
    }
  }

  private byte[] resizePart(MasterSecret masterSecret, MediaConstraints constraints,
                            PduPart part)
      throws IOException, MmsException
  {
    Log.w(TAG, "resizing part " + part.getId());

    final long   oldSize = part.getDataSize();
    final byte[] data    = constraints.getResizedMedia(context, masterSecret, part);

    DatabaseFactory.getPartDatabase(context).updatePartData(masterSecret, part, new ByteArrayInputStream(data));
    Log.w(TAG, String.format("Resized part %.1fkb => %.1fkb", oldSize / 1024.0, part.getDataSize() / 1024.0));

    return data;
  }
}
