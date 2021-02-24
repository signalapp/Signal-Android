/**
 * Copyright (C) 2013 Open Whisper Systems
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

package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase.Reader;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.session.libsignal.utilities.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import network.loki.messenger.R;

public class DatabaseUpgradeActivity extends BaseActivity {
  private static final String TAG = DatabaseUpgradeActivity.class.getSimpleName();

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    VersionTracker.updateLastSeenVersion(this);
    updateNotifications(this);
    startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
    finish();
  }

  public static boolean isUpdate(Context context) {
    int currentVersionCode  = Util.getCanonicalVersionCode();
    int previousVersionCode = VersionTracker.getLastSeenVersion(context);

    return previousVersionCode < currentVersionCode;
  }

  @SuppressLint("StaticFieldLeak")
  private void updateNotifications(final Context context) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        ApplicationContext.getInstance(context).messageNotifier.updateNotification(context);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }
}
