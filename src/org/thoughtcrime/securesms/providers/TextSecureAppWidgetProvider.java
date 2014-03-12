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
import android.view.View;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;

/**
 * The provider for the TextSecure AppWidget
 *
 * @author Lukas Barth
 */
public class TextSecureAppWidgetProvider extends AppWidgetProvider {

  public static void triggerUpdate(Context context) {
    AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
    ComponentName widgetComponent = new ComponentName(context, TextSecureAppWidgetProvider.class);
    int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);

    Intent updateIntent = new Intent();
    updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
    updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    updateIntent.setClass(context, TextSecureAppWidgetProvider.class);
    context.sendBroadcast(updateIntent);
  }

  private int getUnreadCount(Context context) {
    Cursor telcoCursor = null;
    Cursor pushCursor = null;
    int unread = 0;

    try {
      telcoCursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();
      pushCursor = DatabaseFactory.getPushDatabase(context).getPending();

      if (telcoCursor != null) {
        unread += telcoCursor.getCount();
      }

      if (pushCursor != null) {
        unread += pushCursor.getCount();
      }

    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null) pushCursor.close();
    }

    return unread;
  }

  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    final int n = appWidgetIds.length;

    for (int i = 0; i < n; i++) {
      int appWidgetId = appWidgetIds[i];

      Intent intent = new Intent(context, RoutingActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

      RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.textsecure_appwidget);
      views.setOnClickPendingIntent(R.id.icon_view, pendingIntent);

      int unread = getUnreadCount(context);
      if (unread > 0) {
        if (unread > 99) {
          unread = 99;
        }

        views.setTextViewText(R.id.unread_count_text, Integer.toString(unread));
        views.setViewVisibility(R.id.unread_count_text, View.VISIBLE);
      } else {
        views.setViewVisibility(R.id.unread_count_text, View.INVISIBLE);
      }

      appWidgetManager.updateAppWidget(appWidgetId, views);
    }
  }
}
