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
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DynamicIntroTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import kotlin.Unit;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private static final String TAG                       = Log.tag(PassphrasePromptActivity.class);
  private static final short  AUTHENTICATE_REQUEST_CODE = 1007;
  private static final String BUNDLE_ALREADY_SHOWN      = "bundle_already_shown";
  public  static final String FROM_FOREGROUND           = "from_foreground";
  private static final int    HELP_COUNT_THRESHOLD      = 3;

  private DynamicIntroTheme dynamicTheme    = new DynamicIntroTheme();
  private DynamicLanguage   dynamicLanguage = new DynamicLanguage();

  private View            passphraseAuthContainer;
  private ImageView       fingerprintPrompt;
  private TextView        lockScreenButton;

  private EditText        passphraseText;
  private ImageButton     showButton;
  private ImageButton     hideButton;
  private AnimatingToggle visibilityToggle;

  private BiometricManager              biometricManager;
  private BiometricPrompt               biometricPrompt;
  private BiometricDeviceAuthentication biometricAuth;

  private boolean authenticated;
  private boolean hadFailure;
  private boolean alreadyShown;

  private final Runnable resumeScreenLockRunnable = () -> {
    resumeScreenLock(!alreadyShown);
    alreadyShown = true;
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate()");
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(savedInstanceState);

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();

    alreadyShown = (savedInstanceState != null && savedInstanceState.getBoolean(BUNDLE_ALREADY_SHOWN)) ||
                   getIntent().getBooleanExtra(FROM_FOREGROUND, false);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(BUNDLE_ALREADY_SHOWN, alreadyShown);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    setLockTypeVisibility();

    if (TextSecurePreferences.isScreenLockEnabled(this) && !authenticated && !hadFailure) {
      ThreadUtil.postToMain(resumeScreenLockRunnable);
    }

    hadFailure = false;

    fingerprintPrompt.setImageResource(R.drawable.ic_fingerprint_white_48dp);
    fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.signal_accent_primary), PorterDuff.Mode.SRC_IN);
  }

  @Override
  public void onPause() {
    super.onPause();
    ThreadUtil.cancelRunnableOnMain(resumeScreenLockRunnable);
    biometricPrompt.cancelAuthentication();
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

    inflater.inflate(R.menu.passphrase_prompt, menu);

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    if (item.getItemId() == R.id.menu_submit_debug_logs) {
      handleLogSubmit();
      return true;
    } else if (item.getItemId() == R.id.menu_contact_support) {
      sendEmailToSupport();
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode != AUTHENTICATE_REQUEST_CODE) return;

    if (resultCode == RESULT_OK) {
      handleAuthenticated();
    } else {
      Log.w(TAG, "Authentication failed");
      hadFailure = true;
      incrementAttemptCountAndShowHelpIfNecessary();
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
      incrementAttemptCountAndShowHelpIfNecessary();
    }
  }

  private void handleAuthenticated() {
    try {
      authenticated = true;
      
      MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      setMasterSecret(masterSecret);
      SignalStore.misc().setLockScreenAttemptCount(0);
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
    biometricPrompt         = new BiometricPrompt(this, new BiometricAuthenticationListener());
    BiometricPrompt.PromptInfo biometricPromptInfo = new BiometricPrompt.PromptInfo
                                                                        .Builder()
                                                                        .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
                                                                        .setTitle(getString(R.string.PassphrasePromptActivity_unlock_signal))
                                                                        .build();
    biometricAuth = new BiometricDeviceAuthentication(biometricManager, biometricPrompt, biometricPromptInfo);
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

    lockScreenButton.setOnClickListener(v -> resumeScreenLock(true));

    if (SignalStore.misc().getLockScreenAttemptCount() > HELP_COUNT_THRESHOLD) {
      showHelpDialogAndResetAttemptCount(null);
    }
  }

  private void setLockTypeVisibility() {
    if (TextSecurePreferences.isScreenLockEnabled(this)) {
      passphraseAuthContainer.setVisibility(View.GONE);
      fingerprintPrompt.setVisibility(biometricManager.canAuthenticate(BiometricDeviceAuthentication.BIOMETRIC_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS ? View.VISIBLE
                                                                                                                                       : View.GONE);
      lockScreenButton.setVisibility(View.VISIBLE);
    } else {
      passphraseAuthContainer.setVisibility(View.VISIBLE);
      fingerprintPrompt.setVisibility(View.GONE);
      lockScreenButton.setVisibility(View.GONE);
    }
  }

  private void resumeScreenLock(boolean force) {
    if (incrementAttemptCountAndShowHelpIfNecessary(() -> resumeScreenLock(force))) {
      return;
    }

    if (!biometricAuth.authenticate(getApplicationContext(), force, this::showConfirmDeviceCredentialIntent)) {
      handleAuthenticated();
    }
  }

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(this,
                                                            R.string.PassphrasePromptActivity_signal_android_lock_screen,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(this,
                                   SupportEmailUtil.getSupportEmailAddress(this),
                                   getString(R.string.PassphrasePromptActivity_signal_android_lock_screen),
                                   body);
  }

  public Unit showConfirmDeviceCredentialIntent() {
    KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    Intent          intent          = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.PassphrasePromptActivity_unlock_signal), "");

    startActivityForResult(intent, AUTHENTICATE_REQUEST_CODE);
    return Unit.INSTANCE;
  }

  private boolean incrementAttemptCountAndShowHelpIfNecessary() {
    return incrementAttemptCountAndShowHelpIfNecessary(null);
  }

  private boolean incrementAttemptCountAndShowHelpIfNecessary(Runnable onDismissed) {
    SignalStore.misc().incrementLockScreenAttemptCount();

    if (SignalStore.misc().getLockScreenAttemptCount() > HELP_COUNT_THRESHOLD) {
      showHelpDialogAndResetAttemptCount(onDismissed);
      return true;
    }

    return false;
  }

  private void showHelpDialogAndResetAttemptCount(@Nullable Runnable onDismissed) {
    new MaterialAlertDialogBuilder(this)
        .setMessage(R.string.PassphrasePromptActivity_help_prompt_body)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          SignalStore.misc().setLockScreenAttemptCount(0);
          if (onDismissed != null) {
            onDismissed.run();
          }
        })
        .show();
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

  private class BiometricAuthenticationListener extends BiometricPrompt.AuthenticationCallback {
    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errorString) {
      Log.w(TAG, "Authentication error: " + errorCode);
      hadFailure = true;

      incrementAttemptCountAndShowHelpIfNecessary();

      if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
        onAuthenticationFailed();
      }
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");
      fingerprintPrompt.setImageResource(R.drawable.symbol_check_white_48);
      fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.SRC_IN);
      fingerprintPrompt.animate().setInterpolator(new BounceInterpolator()).scaleX(1.1f).scaleY(1.1f).setDuration(500).setListener(new AnimationCompleteListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
          handleAuthenticated();
        }
      }).start();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "onAuthenticationFailed()");

      fingerprintPrompt.setImageResource(R.drawable.symbol_x_white_48);
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
          fingerprintPrompt.getBackground().setColorFilter(getResources().getColor(R.color.signal_accent_primary), PorterDuff.Mode.SRC_IN);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      fingerprintPrompt.startAnimation(shake);
    }
  }
}
