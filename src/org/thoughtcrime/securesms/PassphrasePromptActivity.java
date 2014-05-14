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

import android.content.Intent;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.whispersystems.textsecure.util.Util;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private EditText passphraseText;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.log_submit, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_submit_debug_logs: handleLogSubmit(); return true;
    }

    return false;
  }

  private void handleLogSubmit() {
    Intent intent = new Intent(this, LogSubmitActivity.class);
    startActivity(intent);
  }

  private void initializeResources() {
    mitigateAndroidTilingBug();
    ImageButton okButton = (ImageButton) findViewById(R.id.ok_button);
    passphraseText       = (EditText)    findViewById(R.id.passphrase_edit);
    SpannableString hint = new SpannableString(getString(R.string.PassphrasePromptActivity_enter_passphrase));
    hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    hint.setSpan(new TypefaceSpan("sans-serif"), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    hint.setSpan(new ForegroundColorSpan(0x66000000), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    passphraseText.setHint(hint);
    okButton.setOnClickListener(new OkButtonClickListener());
  }

  private void mitigateAndroidTilingBug() {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      Drawable actionBarBackground = getResources().getDrawable(R.drawable.background_pattern_repeat);
      Util.fixBackgroundRepeat(actionBarBackground);
      getSupportActionBar().setBackgroundDrawable(actionBarBackground);
      Util.fixBackgroundRepeat(findViewById(R.id.scroll_parent).getBackground());
    }
  }

  private class OkButtonClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      try {
        Editable text             = passphraseText.getText();
        String passphrase         = (text == null ? "" : text.toString());
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(PassphrasePromptActivity.this, passphrase);

        MemoryCleaner.clean(passphrase);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException ipe) {
        passphraseText.setText("");
        Toast.makeText(getApplicationContext(),
                       R.string.PassphrasePromptActivity_invalid_passphrase_exclamation,
                       Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void cleanup() {
    this.passphraseText.setText("");
    System.gc();
  }
}
