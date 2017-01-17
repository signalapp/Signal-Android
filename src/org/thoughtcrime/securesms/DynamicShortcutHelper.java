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
public class DynamicShortcutHelper {

    private Context context;
    private ShortcutManager manager;
    private int shorcutNumber = 0;

    public DynamicShortcutHelper(Context context) {
        this.context = context;
        manager = context.getSystemService(ShortcutManager.class);
    }

    public void buildShortcuts() {
        List<ShortcutInfo> infos = new ArrayList<>();
        MasterSecret        masterSecret   = KeyCachingService.getMasterSecret(context);

        if (masterSecret == null) {
            return;
        }

        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        Cursor         cursor         = threadDatabase.getShortcutList();

        try {
            ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));
            ThreadRecord record;

            while ((record = reader.getNext()) != null) {
                final Intent intent = getBaseShareIntent(ConversationActivity.class);
                intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, record.getRecipients().getIds());
                intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, record.getThreadId());
                intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, record.getDistributionType());
                intent.setAction(Intent.ACTION_VIEW);


                //String   name     = record.getRecipients().toShortString();
                Drawable drawable = record.getRecipients().getContactPhoto()
                        .asDrawable(context, record.getRecipients().getColor()
                                .toConversationColor(context));
                Bitmap   avatar   = BitmapUtil.createFromDrawable(drawable, 500, 500);


                Set<String> category = new HashSet<>();
                category.add("android.shortcut.conversation");

                ShortcutInfo info = new ShortcutInfo.Builder(context, record.getRecipients().getPrimaryRecipient().getName())
                        .setIntent(intent)
                        .setRank(infos.size())
                        .setShortLabel(record.getRecipients().getPrimaryRecipient().getName())
                        .setCategories(category)
                        .setIcon(Icon.createWithBitmap(avatar))
                        .build();

                infos.add(info);

                shorcutNumber++;

                if(shorcutNumber > 2) break;
            }

        } finally {
            if (cursor != null) cursor.close();

            manager.setDynamicShortcuts(infos);
        }
    }

    private Intent getBaseShareIntent(final @NonNull Class<?> target) {
        final Intent intent      = new Intent(context, target);
        final String textExtra   = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);

        return intent;
    }
}
