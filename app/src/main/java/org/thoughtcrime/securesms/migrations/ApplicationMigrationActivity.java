/**
 * Copyright (C) 2019 Open Whisper Systems
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

package org.thoughtcrime.securesms.migrations;

import android.os.Bundle;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;

/**
 * An activity that can be shown to block access to the rest of the app when a long-running or
 * otherwise blocking application-level migration is happening.
 */
public class ApplicationMigrationActivity extends BaseActivity {

  private static final String TAG = Log.tag(ApplicationMigrationActivity.class);

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    ApplicationMigrations.getUiBlockingMigrationStatus().observe(this, running -> {
      if (running == null) {
        return;
      }

      if (running) {
        Log.i(TAG, "UI-blocking migration is in progress. Showing spinner.");
        setContentView(R.layout.application_migration_activity);
      } else {
        Log.i(TAG, "UI-blocking migration is no-longer in progress. Finishing.");
        startActivity(getIntent().getParcelableExtra("next_intent"));
        finish();
      }
    });
  }
}
