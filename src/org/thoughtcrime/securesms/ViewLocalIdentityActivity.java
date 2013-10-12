/*
 * Copyright (C) 2011 Whisper Systems
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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;

/**
 * Activity that displays the local identity key and offers the option to regenerate it.
 *
 * @author Moxie Marlinspike
 */
public class ViewLocalIdentityActivity extends ViewIdentityActivity {

  private MasterSecret masterSecret;

  public void onCreate(Bundle bundle) {
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    getIntent().putExtra("identity_key", IdentityKeyUtil.getIdentityKey(this));
    getIntent().putExtra("title", getString(R.string.ApplicationPreferencesActivity_my) + " " +
        getString(R.string.ViewIdentityActivity_identity_fingerprint));
    super.onCreate(bundle);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.local_identity, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_regenerate_key: promptToRegenerateIdentityKey(); return true;
      case android.R.id.home:        finish();                        return true;
    }

    return false;
  }

  private void promptToRegenerateIdentityKey() {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setIcon(android.R.drawable.ic_dialog_alert);
    dialog.setTitle(getString(R.string.ViewLocalIdentityActivity_reset_identity_key));
    dialog.setMessage(getString(R.string.ViewLocalIdentityActivity_by_regenerating_your_identity_key_your_existing_contacts_will_receive_warnings));
    dialog.setNegativeButton(getString(R.string.ViewLocalIdentityActivity_cancel), null);
    dialog.setPositiveButton(getString(R.string.ViewLocalIdentityActivity_continue),
                             new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        regenerateIdentityKey();
      }
    });
    dialog.show();
  }

  private void regenerateIdentityKey() {
    new AsyncTask<Void, Void, Void>() {
      private ProgressDialog progressDialog;

      @Override
      protected void onPreExecute() {
        progressDialog = ProgressDialog.show(ViewLocalIdentityActivity.this,
                                             getString(R.string.ViewLocalIdentityActivity_regenerating),
                                             getString(R.string.ViewLocalIdentityActivity_regenerating_identity_key),
                                             true, false);
      }

      @Override
      public Void doInBackground(Void... params) {
        IdentityKeyUtil.generateIdentityKeys(ViewLocalIdentityActivity.this, masterSecret);
        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        if (progressDialog != null)
          progressDialog.dismiss();

        Toast.makeText(ViewLocalIdentityActivity.this,
                       getString(R.string.ViewLocalIdentityActivity_regenerated),
                       Toast.LENGTH_LONG).show();

        getIntent().putExtra("identity_key",
                             IdentityKeyUtil.getIdentityKey(ViewLocalIdentityActivity.this));
        initialize();
      }

    }.execute();
  }

}
