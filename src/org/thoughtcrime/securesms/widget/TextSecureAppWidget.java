package org.thoughtcrime.securesms.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;

import java.util.ArrayList;
import java.util.List;


/**
 * Implementation of App Widget functionality.
 */
public class TextSecureAppWidget extends AppWidgetProvider {

    private static final String CONVERSATION_URI      = "content://textsecure/thread/";
    private static final String CONVERSATION_LIST_URI = "content://textsecure/conversation-list";

    private static HandlerThread sWorkerThread;
    private static Handler sWorkerQueue;
    private static ConversationListObserver sDataObserver=null;

    public TextSecureAppWidget() {
        sWorkerThread = new HandlerThread("TextSecureAppWidget worker");
        sWorkerThread.start();
        sWorkerQueue = new Handler(sWorkerThread.getLooper());
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Register for external updates to the data to trigger an update of the widget.  When using
        // content providers, the data is often updated via a background service, or in response to
        // user interaction in the main app.  To ensure that the widget always reflects the current
        // state of the data, we must listen for changes and update ourselves accordingly.
        final ContentResolver r = context.getContentResolver();
        if (sDataObserver == null) {
            final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            final ComponentName cn = new ComponentName(context, TextSecureAppWidget.class);
            sDataObserver = new ConversationListObserver(context, mgr, cn, sWorkerQueue);
            r.registerContentObserver(Uri.parse(CONVERSATION_LIST_URI), true, sDataObserver);
        }
        // Get all ids
        ComponentName thisWidget = new ComponentName(context, TextSecureAppWidget.class);
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        // Build the intent to call the service
        Intent intent = new Intent(context.getApplicationContext(), UpdateWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds);
        // Update the widgets via the service
        context.startService(intent);
    }

    @Override
    public void onDisabled(Context context) {
        sDataObserver.unregisterObserver();
        final ContentResolver r = context.getContentResolver();
        r.unregisterContentObserver(sDataObserver);
        sDataObserver=null;

    }

    class ConversationListObserver extends ContentObserver {
        private Context context;
        private AppWidgetManager mAppWidgetManager;
        private ComponentName mComponentName;
        private List<ConversationObserver> observerList;

        ConversationListObserver(Context context, AppWidgetManager mgr, ComponentName cn, Handler h) {
            super(h);
            this.context=context;
            mAppWidgetManager = mgr;
            mComponentName = cn;
            observerList=new ArrayList<ConversationObserver>();
            registerObserver();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);  //NOTE: Have to call this. dwy.
            unregisterObserver();
            registerObserver();
            int[] myWidgetIds = mAppWidgetManager.getAppWidgetIds(mComponentName);
            if(myWidgetIds.length>0){
                // Build the intent to call the service
                Intent intent = new Intent(context.getApplicationContext(), UpdateWidgetService.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, myWidgetIds);
                // Update the widgets via the service
                context.startService(intent);
            }
        }

        protected void registerObserver(){
            final ContentResolver r = context.getContentResolver();
            Cursor c = DatabaseFactory.getThreadDatabase(context).getConversationList();
            c.moveToFirst();
            int colNr=c.getColumnIndex(ThreadDatabase.ID);
            while (!c.isAfterLast()) {
                String id = c.getString(colNr);
                ConversationObserver co = new ConversationObserver(context, mAppWidgetManager, mComponentName, sWorkerQueue);
                r.registerContentObserver(Uri.parse(CONVERSATION_URI+id), true, co);
                observerList.add(co);
                c.moveToNext();
            }
            c.close();
        }

        public void unregisterObserver(){
            final ContentResolver r = context.getContentResolver();
            for(ConversationObserver co:observerList){
                r.unregisterContentObserver(co);
            }
            observerList.clear();
        }
    }

    class ConversationObserver extends ContentObserver {
        private Context context;
        private AppWidgetManager mAppWidgetManager;
        private ComponentName mComponentName;

        ConversationObserver(Context context, AppWidgetManager mgr, ComponentName cn, Handler h) {
            super(h);
            this.context=context;
            mAppWidgetManager = mgr;
            mComponentName = cn;

        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);  //NOTE: Have to call this. dwy.
            int[] myWidgetIds = mAppWidgetManager.getAppWidgetIds(mComponentName);
            if(myWidgetIds.length>0){
                // Build the intent to call the service
                Intent intent = new Intent(context.getApplicationContext(), UpdateWidgetService.class);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, myWidgetIds);
                // Update the widgets via the service
                context.startService(intent);
            }
        }
    }
}


