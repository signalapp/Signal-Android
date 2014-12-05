/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.providers;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.notifications.NotificationState;

/**
 * The provider for the TextSecure AppWidget
 *
 * @author Lukas Barth
 */
public class TextSecureAppWidgetProvider extends AppWidgetProvider {
  private static final String UNREAD_COUNT = "unread_count";

  public static void triggerUpdate(Context context, int unreadCount) {
    AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
    ComponentName widgetComponent = new ComponentName(context, TextSecureAppWidgetProvider.class);
    int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);

    Intent updateIntent = new Intent();
    updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
    updateIntent.putExtra(UNREAD_COUNT, unreadCount);
    updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    updateIntent.setClass(context, TextSecureAppWidgetProvider.class);
    context.sendBroadcast(updateIntent);
  }

  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (!AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) { return; }

    Bundle extras = intent.getExtras();
    if (extras != null) {
      int[] appWidgetIds = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
      int unread = extras.getInt(UNREAD_COUNT);

      if (appWidgetIds != null && appWidgetIds.length > 0) {
        final int n = appWidgetIds.length;

        for (int i = 0; i < n; i++) {
          int appWidgetId = appWidgetIds[i];

          Intent launchIntent = new Intent(context, RoutingActivity.class);
          PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, 0);

          RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.textsecure_appwidget);
          views.setOnClickPendingIntent(R.id.icon_view, pendingIntent);

          if (unread > 0) {
            if (unread > 99) {
              unread = 99;
            }

            views.setTextViewText(R.id.unread_count_text, Integer.toString(unread));
            views.setViewVisibility(R.id.unread_count_text, View.VISIBLE);
          } else {
            views.setViewVisibility(R.id.unread_count_text, View.INVISIBLE);
          }
          AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
        }
      }
    }
  }
}
