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

import android.animation.Animator;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.DynamicIntroTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private static final String TAG = PassphrasePromptActivity.class.getSimpleName();

  private DynamicIntroTheme dynamicTheme    = new DynamicIntroTheme();
  private DynamicLanguage   dynamicLanguage = new DynamicLanguage();

  private ImageView       fingerprintPrompt;

  private EditText        passphraseText;
  private ImageButton     showButton;
  private ImageButton     hideButton;
  private AnimatingToggle visibilityToggle;

  private FingerprintManagerCompat fingerprintManager;
  private CancellationSignal       fingerprintCancellationSignal;
  private FingerprintListener      fingerprintListener;

  private boolean authenticated;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate()");
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    if (TextSecurePreferences.isScreenLockEnabled(this) && !authenticated) {
      resumeScreenLock();
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    if (TextSecurePreferences.isScreenLockEnabled(this)) {
      pauseScreenLock();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
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

  public void onActivityResult(int requestCode, int resultcode, Intent data) {
    if (requestCode != 1) return;

    if (resultcode == RESULT_OK) {
      handleAuthenticated();
    } else {
      Log.w(TAG, "Authentication failed");
      finish();
    }
  }

  private void handleLogSubmit() {
    Intent intent = new Intent(this, LogSubmitActivity.class);
    startActivity(intent);
  }

  private void handlePassphrase() {
    try {
      Editable text             = passphraseText.getText();
      String passphrase         = (text == null ? "" : text.toString());
      MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, passphrase);

      setMasterSecret(masterSecret);
    } catch (InvalidPassphraseException ipe) {
      passphraseText.setText("");
      passphraseText.setError(
              getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation));
    }
  }

  private void handleAuthenticated() {
    try {
      authenticated = true;
      
      MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      setMasterSecret(masterSecret);
    } catch (InvalidPassphraseException e) {
      throw new AssertionError(e);
    }
  }

  private void setPassphraseVisibility(boolean visibility) {
    int cursorPosition = passphraseText.getSelectionStart();
    if (visibility) {
      passphraseText.setInputType(InputType.TYPE_CLASS_TEXT |
                                  InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    } else {
      passphraseText.setInputType(InputType.TYPE_CLASS_TEXT |
                                  InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }
    passphraseText.setSelection(cursorPosition);
  }

  private void initializeResources() {
    View        passphraseAuthContainer = findViewById(R.id.password_auth_container);
    ImageButton okButton                = findViewById(R.id.ok_button);
    Toolbar     toolbar                 = findViewById(R.id.toolbar);

    showButton                    = findViewById(R.id.passphrase_visibility);
    hideButton                    = findViewById(R.id.passphrase_visibility_off);
    visibilityToggle              = findViewById(R.id.button_toggle);
    passphraseText                = findViewById(R.id.passphrase_edit);
    fingerprintPrompt             = findViewById(R.id.fingerprint_auth_container);
    fingerprintManager            = FingerprintManagerCompat.from(this);
    fingerprintCancellationSignal = new CancellationSignal();
    fingerprintListener           = new FingerprintListener();

    setSupportActionBar(toolbar);
    getSupportActionBar().setTitle("");

    SpannableString hint = new SpannableString("  " + getString(R.string.PassphrasePromptActivity_enter_passphrase));
    hint.setSpan(new RelativeSizeSpan(0.9f), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    hint.setSpan(new TypefaceSpan("sans-serif"), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    passphraseText.setHint(hint);
    okButton.setOnClickListener(new OkButtonClickListener());
    showButton.setOnClickListener(new ShowButtonOnClickListener());
    hideButton.setOnClickListener(new HideButtonOnClickListener());
    passphraseText.setOnEditorActionListener(new PassphraseActionListener());
    passphraseText.setImeActionLabel(getString(R.string.prompt_passphrase_activity__unlock),
                                     EditorInfo.IME_ACTION_DONE);

    fingerprintPrompt.setImageResource(R.drawable.ic_fingerprint_white_48dp);
    fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.signal_primary), PorterDuff.Mode.SRC_IN);

    if (TextSecurePreferences.isScreenLockEnabled(this)) {
      passphraseAuthContainer.setVisibility(View.GONE);
      fingerprintPrompt.setVisibility(View.VISIBLE);
    } else {
      passphraseAuthContainer.setVisibility(View.VISIBLE);
      fingerprintPrompt.setVisibility(View.GONE);
    }
  }

  private void resumeScreenLock() {
    KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

    assert keyguardManager != null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !keyguardManager.isKeyguardSecure()) {
      Log.w(TAG ,"Keyguard not secure...");
      handleAuthenticated();
      return;
    }

    if (Build.VERSION.SDK_INT >= 16 && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints()) {
      Log.w(TAG, "Listening for fingerprints...");
      fingerprintCancellationSignal = new CancellationSignal();
      fingerprintManager.authenticate(null, 0, fingerprintCancellationSignal, fingerprintListener, null);
    } else if (Build.VERSION.SDK_INT >= 21){
      Log.w(TAG, "firing intent...");
      Intent intent = keyguardManager.createConfirmDeviceCredentialIntent("Unlock Signal", "");
      startActivityForResult(intent, 1);
    } else {
      Log.w(TAG, "Not compatible...");
      handleAuthenticated();
    }
  }

  private void pauseScreenLock() {
    if (Build.VERSION.SDK_INT >= 16 && fingerprintCancellationSignal != null) {
      fingerprintCancellationSignal.cancel();
    }
  }

  private class PassphraseActionListener implements TextView.OnEditorActionListener {
    @Override
    public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent keyEvent) {
      if ((keyEvent == null && actionId == EditorInfo.IME_ACTION_DONE) ||
          (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
              (actionId == EditorInfo.IME_NULL)))
      {
        handlePassphrase();
        return true;
      } else if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP &&
                 actionId == EditorInfo.IME_NULL)
      {
        return true;
      }

      return false;
    }
  }

  private class OkButtonClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handlePassphrase();
    }
  }

  private class ShowButtonOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      visibilityToggle.display(hideButton);
      setPassphraseVisibility(true);
    }
  }

  private class HideButtonOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      visibilityToggle.display(showButton);
      setPassphraseVisibility(false);
    }
  }

  @Override
  protected void cleanup() {
    this.passphraseText.setText("");
    System.gc();
  }

  private class FingerprintListener extends FingerprintManagerCompat.AuthenticationCallback {
    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
      Log.w(TAG, "Authentication error: " + errMsgId + " " + errString);
      onAuthenticationFailed();
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
      Log.w(TAG, "onAuthenticationSucceeded");
      fingerprintPrompt.setImageResource(R.drawable.ic_check_white_48dp);
      fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.SRC_IN);
      fingerprintPrompt.animate().setInterpolator(new BounceInterpolator()).scaleX(1.1f).scaleY(1.1f).setDuration(500).setListener(new AnimationCompleteListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
          handleAuthenticated();

          fingerprintPrompt.setImageResource(R.drawable.ic_fingerprint_white_48dp);
          fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.signal_primary), PorterDuff.Mode.SRC_IN);
        }
      }).start();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "onAuthenticatoinFailed()");
      FingerprintManagerCompat.AuthenticationCallback callback = this;

      fingerprintPrompt.setImageResource(R.drawable.ic_close_white_48dp);
      fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.SRC_IN);

      TranslateAnimation shake = new TranslateAnimation(0, 30, 0, 0);
      shake.setDuration(50);
      shake.setRepeatCount(7);
      shake.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
          fingerprintPrompt.setImageResource(R.drawable.ic_fingerprint_white_48dp);
          fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.signal_primary), PorterDuff.Mode.SRC_IN);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      fingerprintPrompt.startAnimation(shake);
    }

  }
}
