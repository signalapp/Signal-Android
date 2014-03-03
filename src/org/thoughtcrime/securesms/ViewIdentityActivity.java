/**
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.util.InvalidNumberException;

/**
 * Activity for displaying an identity key.
 *
 * @author Moxie Marlinspike
 */
public class ViewIdentityActivity extends KeyScanningActivity {

  private TextView      identityFingerprint;
  private IdentityKey   identityKey;
  private Recipient     recipient;
  private MasterSecret  masterSecret;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    setContentView(R.layout.view_identity_activity);

    initialize();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    menu.clear();

    MenuInflater inflater = this.getSupportMenuInflater();

    if (this.recipient != null) {
      inflater.inflate(R.menu.review_identity, menu);
    }

    return true;
  }

  protected void initialize() {
    initializeResources();
    initializeFingerprint();
  }

  private void initializeFingerprint() {
    if (identityKey == null) {
      identityFingerprint.setText(R.string.ViewIdentityActivity_you_do_not_have_an_identity_key);
    } else {
      identityFingerprint.setText(identityKey.getFingerprint());
    }
  }

  private void initializeResources() {
    this.identityKey         = (IdentityKey)getIntent().getParcelableExtra("identity_key");
    this.identityFingerprint = (TextView)findViewById(R.id.identity_fingerprint);

    if (getIntent().hasExtra("recipient")) {
      this.recipient = getIntent().getParcelableExtra("recipient");
    } else {
      this.recipient = null;
    }

    if (getIntent().hasExtra("master_secret")) {
      this.masterSecret = getIntent().getParcelableExtra("master_secret");
    } else {
      this.masterSecret = null;
    }

    String title = null;
    if (getIntent().hasExtra("title")) {
      title = getIntent().getStringExtra("title");
    } else if (this.recipient != null) {
      title = recipient.toShortString() +  " " +
            getString(R.string.ViewIdentityActivity_identity_fingerprint);
    }

    if (title != null) {
      getSupportActionBar().setTitle(getIntent().getStringExtra("title"));
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_delete_identity:
        handleDeleteIdentity();
        break;
    }

    return false;
  }

  private void showEndSessionFirstAlert() {
    new AlertDialog.Builder(this)
            .setTitle(R.string.ViewIdentity__alert_end_session_first)
            .setMessage(R.string.ViewIdentity__alert_please_end_your_session_first)
            .setIcon(R.drawable.ic_dialog_alert_holo_light)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {  }
            })
            .show();
  }

  private void handleDeleteIdentity() {
    if (Session.hasSession(this, this.masterSecret, this.recipient)) {
      showEndSessionFirstAlert();
      return;
    }

    Log.i("ViewIdentityAcitivty", "Deleting identity");
    DatabaseFactory.getIdentityDatabase(this).deleteByRecipient(this.recipient.getRecipientId());
    try {
      Log.i("ViewIdentityActivity", "Deleting from directory");
      String e164number = Util.canonicalizeNumber(this, this.recipient.getNumber());
      Directory.getInstance(this).delete(this.recipient.getNumber());
    } catch (InvalidNumberException e) {
      Log.w("ViewIdentityActivity", "Could not delete from directory");
      Log.w("ViewIdentityActivity", e);
    }
    finish();
  }

  @Override
  protected String getScanString() {
    return getString(R.string.ViewIdentityActivity_scan_to_compare);
  }

  @Override
  protected String getDisplayString() {
    return getString(R.string.ViewIdentityActivity_get_scanned_to_compare);
  }

  @Override
  protected IdentityKey getIdentityKeyToCompare() {
    return identityKey;
  }

  @Override
  protected IdentityKey getIdentityKeyToDisplay() {
    return identityKey;
  }

  @Override
  protected String getNotVerifiedMessage() {
    return  getString(R.string.ViewIdentityActivity_warning_the_scanned_key_does_not_match_exclamation);
  }

  @Override
  protected String getNotVerifiedTitle() {
    return getString(R.string.ViewIdentityActivity_not_verified_exclamation);
  }

  @Override
  protected String getVerifiedMessage() {
    return getString(R.string.ViewIdentityActivity_the_scanned_key_matches_exclamation);
  }

  @Override
  protected String getVerifiedTitle() {
    return getString(R.string.ViewIdentityActivity_verified_exclamation);
  }
}
