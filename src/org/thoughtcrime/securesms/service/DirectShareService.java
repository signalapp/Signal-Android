package org.thoughtcrime.securesms.service;


import android.content.ComponentName;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.support.annotation.RequiresApi;

import org.thoughtcrime.securesms.ShareActivity;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.LinkedList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.M)
public class DirectShareService extends ChooserTargetService {

  //Boolean for waiting until all conversations are encrypted
  private boolean waitingUntilConversationsLoaded = true;
  //Refresh rate if contacts are loaded
  private int refreshRate = 2;
  //When contacts are not loaded in 20 seconds, timeout!
  private int timeout = 5;
  private int counter = 0;

  @Override
  public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName,
                                                 IntentFilter matchedFilter)
  {
    List<ChooserTarget> results        = new LinkedList<>();
    MasterSecret        masterSecret   = KeyCachingService.getMasterSecret(this);

    if (masterSecret == null) {
      return results;
    }

    //Wait until all conversations are loaded and ready
    while (waitingUntilConversationsLoaded) {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(this);
      Cursor cursor = threadDatabase.getDirectShareList();

      try {
        ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));
        ThreadRecord record;

        while ((record = reader.getNext()) != null) {
          if (record.getRecipients().getPrimaryRecipient().getName() != null && record.getRecipients().getPrimaryRecipient().getContactPhoto() != null) {

            waitingUntilConversationsLoaded = false;
            break;
          }
        }

      } finally {
        if (cursor != null) cursor.close();
      }

      try {
        Thread.sleep(refreshRate * 1000);
      } catch (Exception exception) {
      }

      //End the thread because timeout!
      if (counter > timeout) waitingUntilConversationsLoaded = false;
      counter++;
    }

    ComponentName  componentName  = new ComponentName(this, ShareActivity.class);
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(this);
    Cursor         cursor         = threadDatabase.getDirectShareList();

    try {
      ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));
      ThreadRecord record;

      while ((record = reader.getNext()) != null && results.size() < 10) {
        if (record.getRecipients().getPrimaryRecipient().getName() != null && record.getRecipients().getPrimaryRecipient().getContactPhoto() != null) {
          Recipients recipients = RecipientFactory.getRecipientsForIds(this, record.getRecipients().getIds(), false);
          String name = recipients.toShortString();
          Drawable drawable = recipients.getContactPhoto().asDrawable(this, recipients.getColor().toConversationColor(this));
          Bitmap avatar = BitmapUtil.createFromDrawable(drawable, 500, 500);

          Bundle bundle = new Bundle();
          bundle.putLong(ShareActivity.EXTRA_THREAD_ID, record.getThreadId());
          bundle.putLongArray(ShareActivity.EXTRA_RECIPIENT_IDS, recipients.getIds());
          bundle.putInt(ShareActivity.EXTRA_DISTRIBUTION_TYPE, record.getDistributionType());

          results.add(new ChooserTarget(name, Icon.createWithBitmap(avatar), 1.0f, componentName, bundle));
        }
      }

      return results;
    } finally {
      if (cursor != null) cursor.close();
    }
  }
}
