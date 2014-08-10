package org.thoughtcrime.securesms.widget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;

/**
 * class handling the widget update
 */
public class UpdateWidgetService extends Service {

    /**
     * callback if service is started.
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// get the widget ids
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());
        int[] allWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        // retrieve the cursor of unread emails, retrieve its count and release it
        Cursor c = DatabaseFactory.getMmsSmsDatabase(getBaseContext()).getUnread();
        int count = c.getCount();
        c.close();
        // update all widgets
		for (int widgetId : allWidgetIds) {
			RemoteViews remoteViews = new RemoteViews(this.getApplicationContext().getPackageName(), R.layout.app_widget);
            if(count>0) {
                // Set the text and the background image
                // if there are more than 9 unread messaged show a '9+' instead of a large number
                remoteViews.setTextViewText(R.id.text, count>9?"9+":String.valueOf(count));
                remoteViews.setImageViewResource(R.id.imageTop, R.drawable.count);
            } else {
                // clear text and background image
                remoteViews.setTextViewText(R.id.text, "");
                remoteViews.setImageViewResource(R.id.imageTop, 0);

            }
			// Register an onClickListener to invoke the main activity
            Intent i = new Intent(this.getApplicationContext(), RoutingActivity.class);
            i.setAction(Intent.ACTION_MAIN);
            PendingIntent pIntent = PendingIntent.getActivity(getApplicationContext(), 0, i, 0);

            remoteViews.setOnClickPendingIntent(R.id.stack, pIntent);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
		stopSelf();
		return super.onStartCommand(intent, flags, startId);
	}

    /**
     * do nothing (there is nothing to communicate), but this allows to instantiate the class
     *
     * @param intent
     * @return
     */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
}