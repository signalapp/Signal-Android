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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
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
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

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

  private DynamicIntroTheme dynamicTheme    = new DynamicIntroTheme();
  private DynamicLanguage   dynamicLanguage = new DynamicLanguage();

  private View                passphraseAuthContainer;
  private LottieAnimationView unlockView;
  private TextView            lockScreenButton;
  private LearnMoreTextView   learnMoreText;

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

    if (SignalStore.settings().getScreenLockEnabled() && !authenticated && !hadFailure) {
      ThreadUtil.postToMain(resumeScreenLockRunnable);
    }

    hadFailure = false;
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
      showHelpDialog();
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
    unlockView              = findViewById(R.id.unlock_view);
    lockScreenButton        = findViewById(R.id.lock_screen_button);
    learnMoreText           = findViewById(R.id.learn_more_text);
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

    lockScreenButton.setOnClickListener(v -> resumeScreenLock(true));
  }

  private void setLockTypeVisibility() {
    if (SignalStore.settings().getScreenLockEnabled()) {
      passphraseAuthContainer.setVisibility(View.GONE);
      unlockView.setVisibility(View.VISIBLE);
      lockScreenButton.setVisibility(View.VISIBLE);
    } else {
      passphraseAuthContainer.setVisibility(View.VISIBLE);
      unlockView.setVisibility(View.GONE);
      lockScreenButton.setVisibility(View.GONE);
    }
  }

  private void resumeScreenLock(boolean force) {
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

  private void showHelpDialog() {
    lockScreenButton.setText(R.string.prompt_passphrase_activity__try_again);

    learnMoreText.setVisibility(View.VISIBLE);
    learnMoreText.setLearnMoreVisible(true);
    learnMoreText.setLinkColor(ContextCompat.getColor(PassphrasePromptActivity.this, R.color.signal_colorPrimary));

    learnMoreText.setOnClickListener(v ->
         new MaterialAlertDialogBuilder(PassphrasePromptActivity.this)
             .setTitle(R.string.prompt_passphrase_activity__unlock_signal)
             .setMessage(R.string.prompt_passphrase_activity__screen_lock_is_on)
             .setCancelable(true)
             .setPositiveButton(android.R.string.ok, null)
             .setNegativeButton(R.string.prompt_passphrase_activity__contact_support, (d,w) -> sendEmailToSupport())
             .show()
    );
  }

  private class BiometricAuthenticationListener extends BiometricPrompt.AuthenticationCallback {
    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errorString) {
      Log.w(TAG, "Authentication error: " + errorCode);
      hadFailure = true;

      showHelpDialog();

      if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
        onAuthenticationFailed();
      }
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");

      lockScreenButton.setOnClickListener(null);
      unlockView.addAnimatorListener(new AnimationCompleteListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
          handleAuthenticated();
        }
      });
      unlockView.playAnimation();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "onAuthenticationFailed()");
      showHelpDialog();
    }
  }
}
