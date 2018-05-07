package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;

import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@RequiresApi(api = Build.VERSION_CODES.N_MR1)
public class ShortcutHelper {

  private Context context;
  private ShortcutManager shortcutManager;

  public ShortcutHelper(Context _context) {
    context = _context;
    shortcutManager = context.getSystemService(ShortcutManager.class);
  }

  //Because we always create chat shortcuts we can use always the same id
  private void reportShortcutUsed() {
    shortcutManager.reportShortcutUsed("add_chat");
  }

  @SuppressLint("StaticFieldLeak")
  public void buildAllShortcuts() {
    //Inform the shortcutmanager that we add a shortcut
    this.reportShortcutUsed();

    //Make shortcutbuilding asynchron so it doesn't throttle down the main thread
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        List<ShortcutInfo> shortcutInfos = new ArrayList<>();

        MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
        if (masterSecret == null) {
          return null;
        }

        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        Cursor cursor = threadDatabase.getShortcutList();

        try {
          ThreadRecord record;
          ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));

          while ((record = reader.getNext()) != null) {
            Recipient recipient = record.getRecipient();
            if (recipient.getName() != null) {
              Intent intent = new Intent(context, ConversationActivity.class);

              //Android Shortcuts don't support objects so we have to use the phone number
              //later we will convert the string back to the address
              if (recipient.getAddress().isPhone()) {
                intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.getAddress().toPhoneString());
              } else if (recipient.getAddress().isGroup()) {
                intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.getAddress().toGroupString());
              }

              intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, record.getThreadId());
              intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, record.getDistributionType());
              intent.putExtra(ConversationActivity.TIMING_EXTRA, System.currentTimeMillis());
              intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, record.getLastSeen());

              intent.setAction(Intent.ACTION_VIEW);

              Drawable drawable;

              ContactPhoto contactPhoto = recipient.getContactPhoto();
              FallbackContactPhoto fallbackContactPhoto = recipient.getFallbackContactPhoto();

              if (contactPhoto != null) {
                try {

                  drawable = GlideApp.with(context)
                    .asDrawable()
                    .load(recipient.getContactPhoto())
                    .circleCrop()
                    .submit(context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                      context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width))
                    .get();

                } catch (InterruptedException | ExecutionException e) {

                  drawable = fallbackContactPhoto.asDrawable(context, recipient.getColor().toConversationColor(context));
                }
              } else {
                drawable = fallbackContactPhoto.asDrawable(context, recipient.getColor().toConversationColor(context));
              }

              Bitmap avatar = BitmapUtil.createFromDrawable(drawable, 500, 500);

              Set<String> category = new HashSet<>();
              category.add("android.shortcut.conversation");

              String shortLabel = record.getRecipient().getName();
              if (shortLabel != null) {
                if (shortLabel.length() > 10) {
                  shortLabel = shortLabel.substring(0, 9);
                  shortLabel = ".";
                }
              } else {
                shortLabel = "ERROR";
              }

              String longLabel = record.getRecipient().getName();
              if (longLabel != null) {
                if (longLabel.length() > 25) {
                  longLabel = longLabel.substring(0, 24);
                  longLabel = ".";
                }
              } else {
                longLabel = "ERROR";
              }

              ShortcutInfo info = new ShortcutInfo.Builder(context, record.getRecipient().getName())
                .setIntent(intent)
                .setRank(shortcutInfos.size())
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setCategories(category)
                .setIcon(Icon.createWithBitmap(avatar))
                .build();

              shortcutInfos.add(info);
            }
          }

        } finally {
          if (cursor != null) cursor.close();

          if (!shortcutInfos.isEmpty()) {
            shortcutManager.setDynamicShortcuts(shortcutInfos);
          }
        }

        return null;
      }
    }.execute();
  }

}
