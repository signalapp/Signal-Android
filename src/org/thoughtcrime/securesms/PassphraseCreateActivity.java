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

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

  private static final String GENERATOR_FILTER = "PassphraseCreateActivity_generatorReceiver";

  private SecretGeneratorReceiver generatorReceiver;

  public PassphraseCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.create_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
    getSupportActionBar().setCustomView(R.layout.centered_app_title);

    generatorReceiver = new SecretGeneratorReceiver();
    LocalBroadcastManager.getInstance(this)
      .registerReceiver(generatorReceiver, new IntentFilter(GENERATOR_FILTER));
    Intent generator = new Intent(this, SecretGenerator.class);
    generator.putExtra("passphrase", MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
    this.startService(generator);
  }

  public void onDestroy() {
    super.onDestroy();
    if (generatorReceiver != null)
      LocalBroadcastManager.getInstance(this).unregisterReceiver(generatorReceiver);
  }

  private class SecretGeneratorReceiver extends BroadcastReceiver {
    private MasterSecret   masterSecret;

    @Override
    public void onReceive(Context receiverContext, Intent receiverIntent) {
      masterSecret = (MasterSecret) receiverIntent.getParcelableExtra("masterSecret");
      setMasterSecret(masterSecret);
    }
  }

  @Override
  protected void cleanup() {
    System.gc();
  }

  public static class SecretGenerator extends IntentService {
    private MasterSecret masterSecret;

    public SecretGenerator() {
      super("SecretGenerator");
    }

    public void onHandleIntent(Intent intent) {
      String passphrase = intent.getStringExtra("passphrase");
      masterSecret = MasterSecretUtil.generateMasterSecret(this, passphrase);
      MasterSecretUtil.generateAsymmetricMasterSecret(this, masterSecret);
      IdentityKeyUtil.generateIdentityKeys(this, masterSecret);
      VersionTracker.updateLastSeenVersion(this);
      TextSecurePreferences.setPasswordDisabled(this, true);

      Intent resultIntent = new Intent(GENERATOR_FILTER);
      resultIntent.putExtra("masterSecret", masterSecret);
      LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
    }
  }
}
