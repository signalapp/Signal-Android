package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.whispersystems.textsecure.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AvatarDownloader {

  private final Context context;

  public AvatarDownloader(Context context) {
    this.context = context.getApplicationContext();
  }

  public void process(MasterSecret masterSecret, Intent intent) {
    try {
      if (!SendReceiveService.DOWNLOAD_AVATAR_ACTION.equals(intent.getAction()))
        return;

      String               groupId  = intent.getStringExtra("group_id");
      GroupDatabase        database = DatabaseFactory.getGroupDatabase(context);
      GroupDatabase.Reader reader   = database.getGroup(groupId);

      GroupDatabase.GroupRecord record;

      while ((record = reader.getNext()) != null) {
        long        avatarId           = record.getAvatarId();
        byte[]      key                = record.getAvatarKey();
        String      relay              = record.getRelay();

        if (avatarId == -1 || key == null) {
          continue;
        }

        File        attachment         = downloadAttachment(relay, avatarId);
        InputStream scaleInputStream   = new AttachmentCipherInputStream(attachment, key);
        InputStream measureInputStream = new AttachmentCipherInputStream(attachment, key);
        Bitmap      avatar             = BitmapUtil.createScaledBitmap(measureInputStream, scaleInputStream, 500, 500);

        database.updateAvatar(groupId, avatar);

        avatar.recycle();
        attachment.delete();
      }
    } catch (IOException e) {
      Log.w("AvatarDownloader", e);
    } catch (InvalidMessageException e) {
      Log.w("AvatarDownloader", e);
    } catch (BitmapDecodingException e) {
      Log.w("AvatarDownloader", e);
    }
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = PushServiceSocketFactory.create(context);
    return socket.retrieveAttachment(relay, contentLocation);
  }

}
