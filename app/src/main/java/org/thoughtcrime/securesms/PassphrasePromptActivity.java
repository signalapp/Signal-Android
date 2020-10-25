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
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;

import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.thoughtcrime.securesms.util.DynamicIntroTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private static final String TAG              = PassphrasePromptActivity.class.getSimpleName();
  private static final int    BIOMETRIC_AUTHNS = Authenticators.BIOMETRIC_STRONG | Authenticators.BIOMETRIC_WEAK;
  private static final int    ALLOWED_AUTHNS   = BIOMETRIC_AUTHNS | Authenticators.DEVICE_CREDENTIAL;

  private DynamicIntroTheme dynamicTheme    = new DynamicIntroTheme();
  private DynamicLanguage   dynamicLanguage = new DynamicLanguage();

  private View            passphraseAuthContainer;
  private ImageView       fingerprintPrompt;
  private TextView        lockScreenButton;

  private EditText        passphraseText;
  private ImageButton     showButton;
  private ImageButton     hideButton;
  private AnimatingToggle visibilityToggle;

  private BiometricManager           biometricManager;
  private BiometricPrompt            biometricPrompt;
  private BiometricPrompt.PromptInfo biometricPromptInfo;

  private boolean authenticated;
  private boolean failure;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate()");
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

    setLockTypeVisibility();

    if (TextSecurePreferences.isScreenLockEnabled(this) && !authenticated && !failure) {
      resumeScreenLock();
    }

    failure = false;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.log_submit, menu);

    super.onCreateOptionsMenu(menu);
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

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    // ActivityResult is returned from a fragment (androidx.biometric.BiometricFragment)
    // so we need to disregard the upper bits in the request code.
    // Request code of `1` was observed in testing androidx.biometric and API 28 and below
    // although it is not documented in anywhere. API 29 and API 30 does not call back
    // onActivityResult.
    // It also happens to be the same request code as we use in `resumeScreenLock` for api
    // versions less than 21. It seems to be a coincidence.
    if ((requestCode & 0xFFFF) != 1) return;

    if (resultCode == RESULT_OK) {
      handleAuthenticated();
    } else {
      Log.w(TAG, "Authentication failed");
      failure = true;
    }
  }

  private void handleLogSubmit() {
    Intent intent = new Intent(this, SubmitDebugLogActivity.class);
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

    ImageButton okButton = findViewById(R.id.ok_button);
    Toolbar     toolbar  = findViewById(R.id.toolbar);

    showButton              = findViewById(R.id.passphrase_visibility);
    hideButton              = findViewById(R.id.passphrase_visibility_off);
    visibilityToggle        = findViewById(R.id.button_toggle);
    passphraseText          = findViewById(R.id.passphrase_edit);
    passphraseAuthContainer = findViewById(R.id.password_auth_container);
    fingerprintPrompt       = findViewById(R.id.fingerprint_auth_container);
    lockScreenButton        = findViewById(R.id.lock_screen_auth_container);
    biometricManager        = BiometricManager.from(this);
    biometricPrompt         = new BiometricPrompt(this, new BiometricAuthListener());
    biometricPromptInfo     = new BiometricPrompt.PromptInfo
                                                 .Builder()
                                                 .setAllowedAuthenticators(ALLOWED_AUTHNS)
                                                 .setTitle(getString(R.string.PassphrasePromptActivity_unlock_signal))
                                                 .build();

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
    fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.core_ultramarine), PorterDuff.Mode.SRC_IN);

    lockScreenButton.setOnClickListener(v -> resumeScreenLock());
  }

  private void setLockTypeVisibility() {
    if (TextSecurePreferences.isScreenLockEnabled(this)) {
      passphraseAuthContainer.setVisibility(View.GONE);
      fingerprintPrompt.setVisibility(
          biometricManager.canAuthenticate(BIOMETRIC_AUTHNS) == BiometricManager.BIOMETRIC_SUCCESS
          ? View.VISIBLE
          : View.GONE);
      lockScreenButton.setVisibility(View.VISIBLE);
    } else {
      passphraseAuthContainer.setVisibility(View.VISIBLE);
      fingerprintPrompt.setVisibility(View.GONE);
      lockScreenButton.setVisibility(View.GONE);
    }
  }

  private void resumeScreenLock() {
    KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

    assert keyguardManager != null;

    if (!keyguardManager.isKeyguardSecure()) {
      Log.w(TAG ,"Keyguard not secure...");
      handleAuthenticated();
      return;
    }

    if (biometricManager.canAuthenticate(ALLOWED_AUTHNS) == BiometricManager.BIOMETRIC_SUCCESS) {
      Log.i(TAG, "Listening for biometric authentication...");
      biometricPrompt.authenticate(biometricPromptInfo);
    } else if (Build.VERSION.SDK_INT >= 21){
      Log.i(TAG, "firing intent...");
      Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.PassphrasePromptActivity_unlock_signal), "");
      startActivityForResult(intent, 1);
    } else {
      Log.w(TAG, "Not compatible...");
      handleAuthenticated();
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

  private class BiometricAuthListener extends BiometricPrompt.AuthenticationCallback {
    @Override
    public void onAuthenticationError(int errMsgId, @NonNull CharSequence errString) {
      Log.w(TAG, "Authentication error: " + errMsgId + " " + errString);
      failure = true;

      // It is too much to show the failure animation when it was that the user canceled
      // the fallback authentication such as pins or patterns to come back to the biometric again.
      if (errMsgId == BiometricPrompt.ERROR_CANCELED || errMsgId == BiometricPrompt.ERROR_USER_CANCELED) return;

      onAuthenticationFailed();
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");
      fingerprintPrompt.setImageResource(R.drawable.ic_check_white_48dp);
      fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.SRC_IN);
      fingerprintPrompt.animate().setInterpolator(new BounceInterpolator()).scaleX(1.1f).scaleY(1.1f).setDuration(500).setListener(new AnimationCompleteListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
          handleAuthenticated();

          fingerprintPrompt.setImageResource(R.drawable.ic_fingerprint_white_48dp);
          fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.core_ultramarine), PorterDuff.Mode.SRC_IN);
        }
      }).start();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "onAuthenticatoinFailed()");

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
          fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.core_ultramarine), PorterDuff.Mode.SRC_IN);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      fingerprintPrompt.startAnimation(shake);
    }
  }
}
