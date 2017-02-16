package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.N_MR1)
public class DynamicShortcutCreator {
    private Context         context;
    private ShortcutManager manager;
    private int             shortcutCounter   = 0;

    public DynamicShortcutCreator(Context context) {
        this.context = context;
        manager      = context.getSystemService(ShortcutManager.class);
    }

    public void buildShortcuts() {
        List<ShortcutInfo> shortcutInfos = new ArrayList<>();

        MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
        if (masterSecret == null) {
            return;
        }

        ThreadDatabase threadDatabase   = DatabaseFactory.getThreadDatabase(context);
        Cursor         cursor           = threadDatabase.getShortcutList();

        try {
            ThreadRecord          record;
            ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));

            while ((record = reader.getNext()) != null) {
                if(record.getRecipients().getPrimaryRecipient().getName() != null) {
                    final Intent intent = getBaseShareIntent(ConversationActivity.class);
                    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, record.getRecipients().getIds());
                    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, record.getThreadId());
                    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, record.getDistributionType());
                    intent.setAction(Intent.ACTION_VIEW);

                    Drawable drawable = record.getRecipients().getContactPhoto()
                            .asDrawable(context, record.getRecipients().getColor()
                                    .toConversationColor(context));
                    Bitmap avatar = BitmapUtil.createFromDrawable(drawable, 500, 500);

                    Set<String> category = new HashSet<>();
                    category.add("android.shortcut.conversation");

                    String shortLabel = record.getRecipients().getPrimaryRecipient().getName();
                    if(shortLabel.length() > 10) {
                        shortLabel = shortLabel.substring(0 ,9);
                        shortLabel += ".";
                    }

                    String longLabel  = record.getRecipients().getPrimaryRecipient().getName();
                    if(longLabel.length() > 25) {
                        longLabel = longLabel.substring(0, 24);
                        longLabel += ".";
                    }

                    ShortcutInfo info = new ShortcutInfo.Builder(context, record.getRecipients().getPrimaryRecipient().getName())
                            .setIntent(intent)
                            .setRank(shortcutInfos.size())
                            .setShortLabel(shortLabel)
                            .setLongLabel(longLabel)
                            .setCategories(category)
                            .setIcon(Icon.createWithBitmap(avatar))
                            .build();

                    shortcutInfos.add(info);

                    shortcutCounter++;
                    if (shortcutCounter > 2) break;
                }
            }

        } finally {
            if (cursor != null) cursor.close();

            if(!shortcutInfos.isEmpty()) {
                manager.setDynamicShortcuts(shortcutInfos);
            }
        }
    }

    private Intent getBaseShareIntent(final @NonNull Class<?> target) {
        final Intent intent = new Intent(context, target);
        final String textExtra = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);

        return intent;
    }
}
