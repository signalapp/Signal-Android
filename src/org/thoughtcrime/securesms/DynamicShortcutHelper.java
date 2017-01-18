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

//Requires minimum SDK 25 (Android 7.1)
@RequiresApi(api = Build.VERSION_CODES.N_MR1)
public class DynamicShortcutHelper implements Runnable{

    private Context context;
    private ShortcutManager manager;
    //If this number is bigger than 2 the app crashes
    private int shorcutNumber = 0;

    //Boolean for waitung untel
    private boolean waitingUntilConversationsLoaded = false;
    //Refresh rate if contacts are loaded
    private int refreshRate = 2;
    //When contacts are not loaded in 20 seconds, timeout!
    private int timeout = 5;
    private int counter = 0;

    //Boolean for Thread
    private boolean running = false;
    //Thread instance
    private Thread thread;

    public DynamicShortcutHelper(Context context) {
        this.thread = new Thread(this);

        this.context = context;
        manager = context.getSystemService(ShortcutManager.class);
    }

    //Starts the thread
    public void buildShortcuts() {
        this.running = true;
        this.thread.start();
    }

    private void stopThread() {
        running=false;
        this.thread.interrupt();
    }

    @Override
    public void run() {
        while(running) {
            while(true) {
                List<ShortcutInfo> infos = new ArrayList<>();
                MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

                if (masterSecret == null) {
                    return;
                }

                ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
                Cursor cursor = threadDatabase.getShortcutList();

                try {
                    ThreadDatabase.Reader reader = threadDatabase.readerFor(cursor, new MasterCipher(masterSecret));
                    ThreadRecord record;

                    while ((record = reader.getNext()) != null) {
                        /* Check the name if it is null.
                           Name is null:
                                Go ahead, and wait 2 seconds
                           Name is not null:
                                Create all shortcuts with the latest three contacts
                         */
                        if (record.getRecipients().getPrimaryRecipient().getName() != null) {
                            final Intent intent = getBaseShareIntent(ConversationActivity.class);
                            intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, record.getRecipients().getIds());
                            intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, record.getThreadId());
                            intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, record.getDistributionType());
                            intent.setAction(Intent.ACTION_VIEW);

                            Drawable drawable = record.getRecipients().getContactPhoto()
                                    .asDrawable(context, record.getRecipients().getColor()
                                            .toConversationColor(context));
                            Bitmap avatar = BitmapUtil.createFromDrawable(drawable, 500, 500);

                            //Category is needed for shortcut
                            Set<String> category = new HashSet<>();
                            category.add("android.shortcut.conversation");

                            //Create shortcut with conversation intent, rank, name, shortname, category and contact photo
                            ShortcutInfo info = new ShortcutInfo.Builder(context, record.getRecipients().getPrimaryRecipient().getName())
                                    .setIntent(intent)
                                    .setRank(infos.size())
                                    .setShortLabel(record.getRecipients().getPrimaryRecipient().getName())
                                    .setCategories(category)
                                    .setIcon(Icon.createWithBitmap(avatar))
                                    .build();

                            infos.add(info);

                            //Check if shortcutnumber is bigger than 2. If it is bigger break the bow
                            shorcutNumber++;
                            if (shorcutNumber > 2) break;

                            //Because the contacts are loaded the while bow can be finished
                            waitingUntilConversationsLoaded = true;

                        }
                    }

                    //Try it every 2 seconds
                    try {
                        Thread.sleep(refreshRate * 1000);

                        //End the thread because timeout!
                        if(counter > timeout) stopThread();
                        counter++;
                    }catch (Exception ex) {
                        Log.i("ShorcutHelper: ", "Thread crashed!");
                    }

                } finally {
                    if (cursor != null) cursor.close();

                    //Set all shortcuts from the list
                    manager.setDynamicShortcuts(infos);

                    //Break the while bow because all is done
                    if(waitingUntilConversationsLoaded) break;
                }
            }

            //End the thread because all shortcuts are set
            stopThread();
        }
    }

    private Intent getBaseShareIntent(final @NonNull Class<?> target) {
        final Intent intent      = new Intent(context, target);
        final String textExtra   = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);

        return intent;
    }
}
