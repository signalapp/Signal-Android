/*
 * Copyright (C) 2011 Whisper Systems
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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.MasterSecret;

/**
 * Activity that displays the local identity key and offers the option to regenerate it.
 *
 * @author Moxie Marlinspike
 */
public class ViewLocalIdentityActivity extends ViewIdentityActivity {

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    getIntent().putExtra(ViewIdentityActivity.IDENTITY_KEY,
                         new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    getIntent().putExtra(ViewIdentityActivity.TITLE,
                         getString(R.string.ViewIdentityActivity_my_identity_fingerprint));

    super.onCreate(icicle, masterSecret);

    /* Make the key on the screen do something when tapped. Since it's our key, the
    obvious choice is to share it with someone or something. */
    identityFingerprint.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, ((TextView) v).getText());
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
      }
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.local_identity, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
        case android.R.id.home:finish(); return true;
    }

    return false;
  }
}
