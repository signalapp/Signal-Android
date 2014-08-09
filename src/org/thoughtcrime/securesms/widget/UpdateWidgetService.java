package org.thoughtcrime.securesms.widget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;

public class UpdateWidgetService extends Service {

    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// create some random data
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());
        int[] allWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        Cursor c = DatabaseFactory.getMmsSmsDatabase(getBaseContext()).getUnread();
        int count = c.getCount();
        c.close();
		for (int widgetId : allWidgetIds) {
			RemoteViews remoteViews = new RemoteViews(this.getApplicationContext().getPackageName(), R.layout.app_widget);
            if(count>0) {
                // Set the text and the background image
                remoteViews.setTextViewText(R.id.text, count>9?"9+":String.valueOf(count));
                remoteViews.setImageViewResource(R.id.imageTop, R.drawable.count);
            } else {
                // clear text and background image
                remoteViews.setTextViewText(R.id.text, "");
                remoteViews.setImageViewResource(R.id.imageTop, 0);

            }
			// Register an onClickListener
            Intent clickIntent = new Intent(this.getApplicationContext(), ConversationListActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, clickIntent, 0);

			remoteViews.setOnClickPendingIntent(R.id.text, pendingIntent);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
		stopSelf();
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
}