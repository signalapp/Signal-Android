package org.thoughtcrime.securesms;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dd.CircularProgressButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.registration.CallMeCountDownView;
import org.thoughtcrime.securesms.components.registration.VerificationCodeView;
import org.thoughtcrime.securesms.components.registration.VerificationPinKeyboard;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.PlayServicesUtil;
import org.thoughtcrime.securesms.util.PlayServicesUtil.PlayServicesStatus;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.List;

/**
 * The register account activity.  Prompts ths user for their registration information
 * and begins the account registration process.
 *
 * @author Moxie Marlinspike
 *
 */
public class RegistrationActivity extends BaseActionBarActivity implements VerificationCodeView.OnCodeEnteredListener {

  private static final int    PICK_COUNTRY              = 1;
  private static final int    SCENE_TRANSITION_DURATION = 250;
  public static final  String CHALLENGE_EVENT           = "org.thoughtcrime.securesms.CHALLENGE_EVENT";
  public static final  String CHALLENGE_EXTRA           = "CAAChallenge";

  private static final String TAG = RegistrationActivity.class.getSimpleName();

  private AsYouTypeFormatter     countryFormatter;
  private ArrayAdapter<String>   countrySpinnerAdapter;
  private Spinner                countrySpinner;
  private TextView               countryCode;
  private TextView               number;
  private CircularProgressButton createButton;
  private TextView               informationView;
  private TextView               informationToggleText;
  private TextView               title;
  private TextView               subtitle;
  private View                   registrationContainer;
  private View                   verificationContainer;
  private FloatingActionButton   fab;

  private CallMeCountDownView         callMeCountDownView;
  private VerificationPinKeyboard     keyboard;
  private VerificationCodeView        verificationCodeView;
  private RegistrationState           registrationState;
  private ChallengeReceiver           challengeReceiver;
  private SignalServiceAccountManager accountManager;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.registration_activity);

    initializeResources();
    initializeSpinner();
    initializePermissions();
    initializeNumber();
    initializeChallengeListener();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownChallengeListener();
    markAsVerifying(false);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PICK_COUNTRY && resultCode == RESULT_OK && data != null) {
      this.countryCode.setText(String.valueOf(data.getIntExtra("country_code", 1)));
      setCountryDisplay(data.getStringExtra("country_name"));
      setCountryFormatter(data.getIntExtra("country_code", 1));
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void initializeResources() {
    TextView     skipButton        = findViewById(R.id.skip_button);
    View         informationToggle = findViewById(R.id.information_link_container);

    this.countrySpinner        = findViewById(R.id.country_spinner);
    this.countryCode           = findViewById(R.id.country_code);
    this.number                = findViewById(R.id.number);
    this.createButton          = findViewById(R.id.registerButton);
    this.informationView       = findViewById(R.id.registration_information);
    this.informationToggleText = findViewById(R.id.information_label);
    this.title                 = findViewById(R.id.verify_header);
    this.subtitle              = findViewById(R.id.verify_subheader);
    this.registrationContainer = findViewById(R.id.registration_container);
    this.verificationContainer = findViewById(R.id.verification_container);
    this.fab                   = findViewById(R.id.fab);

    this.verificationCodeView = findViewById(R.id.code);
    this.keyboard             = findViewById(R.id.keyboard);
    this.callMeCountDownView  = findViewById(R.id.call_me_count_down);
    this.registrationState    = new RegistrationState(RegistrationState.State.INITIAL, null, null, null);

    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
    this.number.addTextChangedListener(new NumberChangedListener());
    this.createButton.setOnClickListener(v -> handleRegister());
    this.callMeCountDownView.setOnClickListener(v -> handlePhoneCallRequest());
    skipButton.setOnClickListener(v -> handleCancel());
    informationToggle.setOnClickListener(new InformationToggleListener());

    if (getIntent().getBooleanExtra("cancel_button", false)) {
      skipButton.setVisibility(View.VISIBLE);
    } else {
      skipButton.setVisibility(View.INVISIBLE);
    }

    this.keyboard.setOnKeyPressListener(key -> {
      if (key >= 0) verificationCodeView.append(key);
      else          verificationCodeView.delete();
    });

    this.verificationCodeView.setOnCompleteListener(this);
  }

  @SuppressLint("ClickableViewAccessibility")
  private void initializeSpinner() {
    this.countrySpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));

    this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
    this.countrySpinner.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        Intent intent = new Intent(RegistrationActivity.this, CountrySelectionActivity.class);
        startActivityForResult(intent, PICK_COUNTRY);
      }
      return true;
    });
    this.countrySpinner.setOnKeyListener((v, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
        Intent intent = new Intent(RegistrationActivity.this, CountrySelectionActivity.class);
        startActivityForResult(intent, PICK_COUNTRY);
        return true;
      }
      return false;
    });
  }

  @SuppressLint("MissingPermission")
  private void initializeNumber() {
    Optional<Phonenumber.PhoneNumber> localNumber = Optional.absent();

    if (Permissions.hasAll(this, Manifest.permission.READ_PHONE_STATE)) {
      localNumber = Util.getDeviceNumber(this);
    }

    if (localNumber.isPresent()) {
      this.countryCode.setText(String.valueOf(localNumber.get().getCountryCode()));
      this.number.setText(String.valueOf(localNumber.get().getNationalNumber()));
    } else {
      Optional<String> simCountryIso = Util.getSimCountryIso(this);

      if (simCountryIso.isPresent() && !TextUtils.isEmpty(simCountryIso.get())) {
        this.countryCode.setText(String.valueOf(PhoneNumberUtil.getInstance().getCountryCodeForRegion(simCountryIso.get())));
      }
    }
  }

  @SuppressLint("InlinedApi")
  private void initializePermissions() {
    Permissions.with(RegistrationActivity.this)
               .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.PROCESS_OUTGOING_CALLS)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends),
                                    R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp)
               .onSomeGranted(permissions -> {
                 if (permissions.contains(Manifest.permission.READ_PHONE_STATE)) {
                   initializeNumber();
                 }
               })
               .execute();
  }

  private void setCountryDisplay(String value) {
    this.countrySpinnerAdapter.clear();
    this.countrySpinnerAdapter.add(value);
  }

  private void setCountryFormatter(int countryCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    String regionCode    = util.getRegionCodeForCountryCode(countryCode);

    if (regionCode == null) this.countryFormatter = null;
    else                    this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  private void handleRegister() {
    if (TextUtils.isEmpty(countryCode.getText())) {
      Toast.makeText(this, getString(R.string.RegistrationActivity_you_must_specify_your_country_code), Toast.LENGTH_LONG).show();
      return;
    }

    if (TextUtils.isEmpty(number.getText())) {
      Toast.makeText(this, getString(R.string.RegistrationActivity_you_must_specify_your_phone_number), Toast.LENGTH_LONG).show();
      return;
    }

    Permissions.with(this)
               .request(Manifest.permission.READ_SMS)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.RegistrationActivity_to_easily_verify_your_phone_number_signal_can_automatically_detect_your_verification_code), R.drawable.ic_textsms_white_48dp)
               .onAnyResult(this::handleRegisterWithPermissions)
               .execute();
  }

  private void handleRegisterWithPermissions() {
    if (TextUtils.isEmpty(countryCode.getText())) {
      Toast.makeText(this, getString(R.string.RegistrationActivity_you_must_specify_your_country_code), Toast.LENGTH_LONG).show();
      return;
    }

    if (TextUtils.isEmpty(number.getText())) {
      Toast.makeText(this, getString(R.string.RegistrationActivity_you_must_specify_your_phone_number), Toast.LENGTH_LONG).show();
      return;
    }

    final String e164number = getConfiguredE164Number();

    if (!PhoneNumberFormatter.isValidNumber(e164number)) {
      Dialogs.showAlertDialog(this, getString(R.string.RegistrationActivity_invalid_number),
                              String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
                                            e164number));
      return;
    }

    PlayServicesStatus gcmStatus = PlayServicesUtil.getPlayServicesStatus(this);

    if (gcmStatus == PlayServicesStatus.SUCCESS) {
      handleRequestVerification(e164number, true);
    } else if (gcmStatus == PlayServicesStatus.MISSING) {
      handlePromptForNoPlayServices(e164number);
    } else if (gcmStatus == PlayServicesStatus.NEEDS_UPDATE) {
      GoogleApiAvailability.getInstance().getErrorDialog(this, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
    } else {
      Dialogs.showAlertDialog(this, getString(R.string.RegistrationActivity_play_services_error),
                              getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void handleRequestVerification(@NonNull String e164number, boolean gcmSupported) {
    createButton.setIndeterminateProgressMode(true);
    createButton.setProgress(50);

    new AsyncTask<Void, Void, Pair<String, Optional<String>>> () {
      @Override
      protected @Nullable Pair<String, Optional<String>> doInBackground(Void... voids) {
        try {
          markAsVerifying(true);

          String password = Util.getSecret(18);

          Optional<String> gcmToken;

          if (gcmSupported) {
            gcmToken = Optional.of(GoogleCloudMessaging.getInstance(RegistrationActivity.this).register(GcmRefreshJob.REGISTRATION_ID));
          } else {
            gcmToken = Optional.absent();
          }

          accountManager = AccountManagerFactory.createManager(RegistrationActivity.this, e164number, password);
          accountManager.requestSmsVerificationCode();

          return new Pair<>(password, gcmToken);
        } catch (IOException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      protected void onPostExecute(@Nullable Pair<String, Optional<String>> result) {
        if (result == null) {
          Toast.makeText(RegistrationActivity.this, R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show();
          return;
        }

        registrationState = new RegistrationState(RegistrationState.State.VERIFYING, e164number, result.first, result.second);
        displayVerificationView(e164number, 64);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleChallengeReceived(@Nullable String challenge) {
    if (challenge != null && challenge.length() == 6 && registrationState.state == RegistrationState.State.VERIFYING) {
      verificationCodeView.clear();

      try {
        for (int i=0;i<challenge.length();i++) {
          final int index = i;
          verificationCodeView.postDelayed(() -> verificationCodeView.append(Integer.parseInt(Character.toString(challenge.charAt(index)))), i * 200);
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, e);
        verificationCodeView.clear();
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onCodeComplete(@NonNull String code) {
    this.registrationState = new RegistrationState(RegistrationState.State.CHECKING, this.registrationState);
    callMeCountDownView.setVisibility(View.INVISIBLE);
    keyboard.displayProgress();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... voids) {
        try {
          int registrationId = KeyHelper.generateRegistrationId(false);
          TextSecurePreferences.setLocalRegistrationId(RegistrationActivity.this, registrationId);
          SessionUtil.archiveAllSessions(RegistrationActivity.this);

          String signalingKey = Util.getSecret(52);

          accountManager.verifyAccountWithCode(code, signalingKey, registrationId, !registrationState.gcmToken.isPresent());

          IdentityKeyPair    identityKey  = IdentityKeyUtil.getIdentityKeyPair(RegistrationActivity.this);
          List<PreKeyRecord> records      = PreKeyUtil.generatePreKeys(RegistrationActivity.this);
          SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(RegistrationActivity.this, identityKey, true);

          accountManager.setPreKeys(identityKey.getPublicKey(), signedPreKey, records);

          if (registrationState.gcmToken.isPresent()) {
            accountManager.setGcmId(registrationState.gcmToken);
          }

          TextSecurePreferences.setGcmRegistrationId(RegistrationActivity.this, registrationState.gcmToken.orNull());
          TextSecurePreferences.setGcmDisabled(RegistrationActivity.this, !registrationState.gcmToken.isPresent());
          TextSecurePreferences.setWebsocketRegistered(RegistrationActivity.this, true);

          DatabaseFactory.getIdentityDatabase(RegistrationActivity.this)
                         .saveIdentity(Address.fromSerialized(registrationState.e164number),
                                       identityKey.getPublicKey(), IdentityDatabase.VerifiedStatus.VERIFIED,
                                       true, System.currentTimeMillis(), true);

          TextSecurePreferences.setVerifying(RegistrationActivity.this, false);
          TextSecurePreferences.setPushRegistered(RegistrationActivity.this, true);
          TextSecurePreferences.setLocalNumber(RegistrationActivity.this, registrationState.e164number);
          TextSecurePreferences.setPushServerPassword(RegistrationActivity.this, registrationState.password);
          TextSecurePreferences.setSignalingKey(RegistrationActivity.this, signalingKey);
          TextSecurePreferences.setSignedPreKeyRegistered(RegistrationActivity.this, true);
          TextSecurePreferences.setPromptedPushRegistration(RegistrationActivity.this, true);
          TextSecurePreferences.setUnauthorizedReceived(RegistrationActivity.this, false);

          return true;
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }
      }

      @Override
      protected void onPostExecute(Boolean result) {
        if (result) {
          keyboard.displaySuccess().addListener(new AssertedSuccessListener<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              ApplicationContext.getInstance(RegistrationActivity.this).getJobManager().add(new DirectoryRefreshJob(RegistrationActivity.this, false));

              DirectoryRefreshListener.schedule(RegistrationActivity.this);
              RotateSignedPreKeyListener.schedule(RegistrationActivity.this);

              Intent nextIntent = getIntent().getParcelableExtra("next_intent");

              if (nextIntent == null) {
                nextIntent = new Intent(RegistrationActivity.this, ConversationListActivity.class);
              }

              startActivity(nextIntent);
              finish();
            }
          });
        } else {
          keyboard.displayFailure().addListener(new AssertedSuccessListener<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              registrationState = new RegistrationState(RegistrationState.State.VERIFYING, registrationState);
              callMeCountDownView.setVisibility(View.VISIBLE);
              verificationCodeView.clear();
              keyboard.displayKeyboard();
            }
          });
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @SuppressLint("StaticFieldLeak")
  private void handlePhoneCallRequest() {
    if (registrationState.state == RegistrationState.State.VERIFYING) {
      callMeCountDownView.startCountDown(300);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... voids) {
          try {
            accountManager.requestVoiceVerificationCode();
          } catch (IOException e) {
            Log.w(TAG, e);
          }

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void displayInitialView(@NonNull String e164number) {
    title.animate().translationX(title.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        title.setText(R.string.registration_activity__verify_your_number);
        title.clearAnimation();
        title.setTranslationX(-1 * title.getWidth());
        title.animate().translationX(0).setListener(null).setInterpolator(new OvershootInterpolator()).setDuration(SCENE_TRANSITION_DURATION).start();
      }
    }).start();

    subtitle.animate().translationX(subtitle.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        subtitle.setText(R.string.registration_activity__please_enter_your_mobile_number_to_receive_a_verification_code_carrier_rates_may_apply);
        subtitle.clearAnimation();
        subtitle.setTranslationX(-1 * subtitle.getWidth());
        subtitle.animate().translationX(0).setListener(null).setInterpolator(new OvershootInterpolator()).setDuration(SCENE_TRANSITION_DURATION).start();
      }
    }).start();

    verificationContainer.animate().translationX(verificationContainer.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        verificationContainer.clearAnimation();
        verificationContainer.setVisibility(View.INVISIBLE);
        verificationContainer.setTranslationX(0);

        registrationContainer.setTranslationX(-1 * registrationContainer.getWidth());
        registrationContainer.setVisibility(View.VISIBLE);
        createButton.setProgress(0);
        createButton.setIndeterminateProgressMode(false);
        registrationContainer.animate().translationX(0).setDuration(SCENE_TRANSITION_DURATION).setListener(null).setInterpolator(new OvershootInterpolator()).start();
      }
    }).start();

    fab.animate().rotationBy(360f).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        fab.clearAnimation();
        fab.setImageResource(R.drawable.ic_action_name);
        fab.animate().rotationBy(375f).setDuration(SCENE_TRANSITION_DURATION).setListener(null).start();
      }
    }).start();
  }

  private void displayVerificationView(@NonNull String e164number, int callCountdown) {
    ServiceUtil.getInputMethodManager(this)
               .hideSoftInputFromWindow(countryCode.getWindowToken(), 0);

    ServiceUtil.getInputMethodManager(this)
               .hideSoftInputFromWindow(number.getWindowToken(), 0);

    title.animate().translationX(-1 * title.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        title.setText(String.format("Verify %s", e164number));
        title.clearAnimation();
        title.setTranslationX(title.getWidth());
        title.animate().translationX(0).setListener(null).setInterpolator(new OvershootInterpolator()).setDuration(SCENE_TRANSITION_DURATION).start();
      }
    }).start();

    subtitle.animate().translationX(-1 * subtitle.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        SpannableString subtitleDescription = new SpannableString(String.format("Please enter the verification code sent to %s.", e164number));
        SpannableString wrongNumber         = new SpannableString("Wrong number?");

        ClickableSpan clickableSpan = new ClickableSpan() {
          @Override
          public void onClick(View widget) {
            displayInitialView(e164number);
            registrationState = new RegistrationState(RegistrationState.State.INITIAL, null, null, null);
          }

          @Override
          public void updateDrawState(TextPaint paint) {
            paint.setColor(Color.WHITE);
            paint.setUnderlineText(true);
          }
        };

        wrongNumber.setSpan(clickableSpan, 0, wrongNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        subtitle.setText(new SpannableStringBuilder(subtitleDescription).append(" ").append(wrongNumber));
        subtitle.setMovementMethod(LinkMovementMethod.getInstance());
        subtitle.clearAnimation();
        subtitle.setTranslationX(subtitle.getWidth());
        subtitle.animate().translationX(0).setListener(null).setInterpolator(new OvershootInterpolator()).setDuration(SCENE_TRANSITION_DURATION).start();
      }
    }).start();

    registrationContainer.animate().translationX(-1 * registrationContainer.getWidth()).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        registrationContainer.clearAnimation();
        registrationContainer.setVisibility(View.INVISIBLE);
        registrationContainer.setTranslationX(0);

        verificationContainer.setTranslationX(verificationContainer.getWidth());
        verificationContainer.setVisibility(View.VISIBLE);
        verificationContainer.animate().translationX(0).setListener(null).setInterpolator(new OvershootInterpolator()).setDuration(SCENE_TRANSITION_DURATION).start();
      }
    }).start();

    fab.animate().rotationBy(-360f).setDuration(SCENE_TRANSITION_DURATION).setListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        fab.clearAnimation();
        fab.setImageResource(R.drawable.ic_textsms_24dp);
        fab.animate().rotationBy(-375f).setDuration(SCENE_TRANSITION_DURATION).setListener(null).start();
      }
    }).start();

    this.callMeCountDownView.startCountDown(callCountdown);
  }

  private void handleCancel() {
    TextSecurePreferences.setPromptedPushRegistration(RegistrationActivity.this, true);
    Intent nextIntent = getIntent().getParcelableExtra("next_intent");

    if (nextIntent == null) {
      nextIntent = new Intent(RegistrationActivity.this, ConversationListActivity.class);
    }

    startActivity(nextIntent);
    finish();
  }

  private void handlePromptForNoPlayServices(@NonNull String e164number) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(R.string.RegistrationActivity_missing_google_play_services);
    dialog.setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services);
    dialog.setPositiveButton(R.string.RegistrationActivity_i_understand, (dialog1, which) -> handleRequestVerification(e164number, false));
    dialog.setNegativeButton(android.R.string.cancel, null);
    dialog.show();
  }

  private void initializeChallengeListener() {
    challengeReceiver = new ChallengeReceiver();
    IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
    registerReceiver(challengeReceiver, filter);
  }

  private void shutdownChallengeListener() {
    if (challengeReceiver != null) {
      unregisterReceiver(challengeReceiver);
      challengeReceiver = null;
    }
  }

  private void markAsVerifying(boolean verifying) {
    TextSecurePreferences.setVerifying(this, verifying);

    if (verifying) {
      TextSecurePreferences.setPushRegistered(this, false);
    }
  }

  private class ChallengeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "Got a challenge broadcast...");
      handleChallengeReceived(intent.getStringExtra(CHALLENGE_EXTRA));
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (TextUtils.isEmpty(s) || !TextUtils.isDigitsOnly(s)) {
        setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!TextUtils.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      if (countryFormatter == null)
        return;

      if (TextUtils.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
        s.replace(0, s.length(), formattedNumber);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  private class InformationToggleListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if (informationView.getVisibility() == View.VISIBLE) {
        informationView.setVisibility(View.GONE);
        informationToggleText.setText(R.string.RegistrationActivity_more_information);
      } else {
        informationView.setVisibility(View.VISIBLE);
        informationToggleText.setText(R.string.RegistrationActivity_less_information);
      }
    }
  }

  private static class RegistrationState {
    private enum State {
      INITIAL, VERIFYING, CHECKING
    }

    private final State   state;
    private final String  e164number;
    private final String  password;
    private final Optional<String> gcmToken;

    RegistrationState(State state, String e164number, String password, Optional<String> gcmToken) {
      this.state      = state;
      this.e164number = e164number;
      this.password   = password;
      this.gcmToken   = gcmToken;
    }

    RegistrationState(State state, RegistrationState previous) {
      this.state      = state;
      this.e164number = previous.e164number;
      this.password   = previous.password;
      this.gcmToken   = previous.gcmToken;
    }
  }
}
