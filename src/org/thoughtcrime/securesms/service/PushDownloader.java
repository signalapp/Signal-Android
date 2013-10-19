package org.thoughtcrime.securesms.service;


import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingPartDatabase;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.TextSecurePushCredentials;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;

public class PushDownloader {

  private final Context context;

  public PushDownloader(Context context) {
    this.context = context.getApplicationContext();
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    if (!intent.getAction().equals(SendReceiveService.DOWNLOAD_PUSH_ACTION))
      return;

    long         messageId = intent.getLongExtra("message_id", -1);
    PartDatabase database  = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);

    Log.w("PushDownloader", "Downloading push parts for: " + messageId);

    if (messageId != -1) {
      List<Pair<Long, PduPart>> parts = database.getParts(messageId, false);

      for (Pair<Long, PduPart> partPair : parts) {
        retrievePart(masterSecret, partPair.second, messageId, partPair.first);
        Log.w("PushDownloader", "Got part: " + partPair.first);
      }
    } else {
      List<Pair<Long, Pair<Long, PduPart>>> parts = database.getPushPendingParts();

      for (Pair<Long, Pair<Long, PduPart>> partPair : parts) {
        retrievePart(masterSecret, partPair.second.second, partPair.first, partPair.second.first);
        Log.w("PushDownloader", "Got part: " + partPair.second.first);
      }
    }
  }

  private void retrievePart(MasterSecret masterSecret, PduPart part, long messageId, long partId) {
    EncryptingPartDatabase database       = DatabaseFactory.getEncryptingPartDatabase(context, masterSecret);
    File                   attachmentFile = null;

    try {
      MasterCipher masterCipher    = new MasterCipher(masterSecret);
      long         contentLocation = Long.parseLong(Util.toIsoString(part.getContentLocation()));
      byte[]       key             = masterCipher.decryptBytes(Base64.decode(Util.toIsoString(part.getContentDisposition())));

      attachmentFile              = downloadAttachment(contentLocation);
      InputStream attachmentInput = new AttachmentCipherInputStream(attachmentFile, key);

      database.updateDownloadedPart(messageId, partId, part, attachmentInput);
    } catch (InvalidMessageException e) {
      Log.w("PushDownloader", e);
      try {
        database.updateFailedDownloadedPart(messageId, partId, part);
      } catch (MmsException mme) {
        Log.w("PushDownloader", mme);
      }
    } catch (MmsException e) {
      Log.w("PushDownloader", e);
      try {
        database.updateFailedDownloadedPart(messageId, partId, part);
      } catch (MmsException mme) {
        Log.w("PushDownloader", mme);
      }
    } catch (IOException e) {
      Log.w("PushDownloader", e);
      /// XXX schedule some kind of soft failure retry action
    } finally {
      if (attachmentFile != null)
        attachmentFile.delete();
    }
  }

  private File downloadAttachment(long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(context, TextSecurePushCredentials.getInstance());
    return socket.retrieveAttachment(contentLocation);
  }

}
