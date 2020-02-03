package org.thoughtcrime.securesms.lock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.DialogCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

import java.io.IOException;

public final class RegistrationLockDialog {

  private static final String TAG = Log.tag(RegistrationLockDialog.class);

  public static void showReminderIfNecessary(@NonNull Fragment fragment) {
    final Context context = fragment.requireContext();

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
    if (!RegistrationLockReminders.needsReminder(context))    return;

    if (!TextSecurePreferences.isV1RegistrationLockEnabled(context) &&
        !SignalStore.kbsValues().isV2RegistrationLockEnabled()) {
      // Neither v1 or v2 to check against
      Log.w(TAG, "Reg lock enabled, but no pin stored to verify against");
      return;
    }

    if (FeatureFlags.pinsForAll()) {
      showReminder(context, fragment);
    } else {
      showLegacyPinReminder(context);
    }
  }

  private static void showReminder(@NonNull Context context, @NonNull Fragment fragment) {
    AlertDialog dialog = new AlertDialog.Builder(context, ThemeUtil.isDarkTheme(context) ? R.style.RationaleDialogDark_SignalAccent : R.style.RationaleDialogLight_SignalAccent)
                                        .setView(R.layout.kbs_pin_reminder_view)
                                        .setCancelable(false)
                                        .setOnCancelListener(d -> RegistrationLockReminders.scheduleReminder(context, false))
                                        .create();

    WindowManager  windowManager = ServiceUtil.getWindowManager(context);
    Display        display       = windowManager.getDefaultDisplay();
    DisplayMetrics metrics       = new DisplayMetrics();
    display.getMetrics(metrics);

    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    dialog.show();
    dialog.getWindow().setLayout((int)(metrics.widthPixels * .80), ViewGroup.LayoutParams.WRAP_CONTENT);

    TextInputLayout pinWrapper  = (TextInputLayout) DialogCompat.requireViewById(dialog, R.id.pin_wrapper);
    EditText        pinEditText = (EditText) DialogCompat.requireViewById(dialog, R.id.pin);
    TextView        reminder    = (TextView) DialogCompat.requireViewById(dialog, R.id.reminder);
    View            skip        = DialogCompat.requireViewById(dialog, R.id.skip);
    View            submit      = DialogCompat.requireViewById(dialog, R.id.submit);

    SpannableString reminderText = new SpannableString(context.getString(R.string.KbsReminderDialog__to_help_you_memorize_your_pin));
    SpannableString forgotText   = new SpannableString(context.getString(R.string.KbsReminderDialog__forgot_pin));

    pinEditText.requestFocus();

    switch (SignalStore.kbsValues().getKeyboardType()) {
      case NUMERIC:
        pinEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        break;
      case ALPHA_NUMERIC:
        pinEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        break;
    }

    ClickableSpan clickableSpan = new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        dialog.dismiss();
        RegistrationLockReminders.scheduleReminder(context, true);

        fragment.startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromForgotPin(context), CreateKbsPinActivity.REQUEST_NEW_PIN);
      }
    };

    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    skip.setOnClickListener(v -> {
      dialog.dismiss();
      RegistrationLockReminders.scheduleReminder(context, false);
    });

    PinVerifier.Callback callback = getPinWatcherCallback(context, dialog, pinWrapper);
    PinVerifier          verifier = SignalStore.kbsValues().isV2RegistrationLockEnabled()
                                    ? new V2PinVerifier()
                                    : new V1PinVerifier(context);

    submit.setOnClickListener(v -> {
      Editable pinEditable = pinEditText.getText();

      verifier.verifyPin(pinEditable == null ? null : pinEditable.toString(), callback);
    });
  }

  /**
   * @deprecated TODO [alex]: Remove after pins for all live.
   */
  @Deprecated
  private static void showLegacyPinReminder(@NonNull Context context) {
    AlertDialog dialog = new AlertDialog.Builder(context, ThemeUtil.isDarkTheme(context) ? R.style.RationaleDialogDark : R.style.RationaleDialogLight)
                                        .setView(R.layout.registration_lock_reminder_view)
                                        .setCancelable(true)
                                        .setOnCancelListener(d -> RegistrationLockReminders.scheduleReminder(context, false))
                                        .create();

    WindowManager  windowManager = ServiceUtil.getWindowManager(context);
    Display        display       = windowManager.getDefaultDisplay();
    DisplayMetrics metrics       = new DisplayMetrics();
    display.getMetrics(metrics);

    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    dialog.show();
    dialog.getWindow().setLayout((int)(metrics.widthPixels * .80), ViewGroup.LayoutParams.WRAP_CONTENT);

    EditText pinEditText = (EditText) DialogCompat.requireViewById(dialog, R.id.pin);
    TextView reminder    = (TextView) DialogCompat.requireViewById(dialog, R.id.reminder);

    if (pinEditText == null) throw new AssertionError();
    if (reminder    == null) throw new AssertionError();

    SpannableString reminderIntro = new SpannableString(context.getString(R.string.RegistrationLockDialog_reminder));
    SpannableString reminderText  = new SpannableString(context.getString(R.string.RegistrationLockDialog_registration_lock_is_enabled_for_your_phone_number));
    SpannableString forgotText    = new SpannableString(context.getString(R.string.RegistrationLockDialog_i_forgot_my_pin));

    ClickableSpan clickableSpan = new ClickableSpan() {
      @Override
      public void onClick(@NonNull View widget) {
        dialog.dismiss();
        new AlertDialog.Builder(context).setTitle(R.string.RegistrationLockDialog_forgotten_pin)
                                        .setMessage(R.string.RegistrationLockDialog_registration_lock_helps_protect_your_phone_number_from_unauthorized_registration_attempts)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .create()
                                        .show();
      }
    };

    reminderIntro.setSpan(new StyleSpan(Typeface.BOLD), 0, reminderIntro.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderIntro).append(" ").append(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    pinEditText.addTextChangedListener(SignalStore.kbsValues().isV2RegistrationLockEnabled()
                                       ? getV2PinWatcher(context, dialog)
                                       : getV1PinWatcher(context, dialog));
  }

  private static TextWatcher getV1PinWatcher(@NonNull Context context, AlertDialog dialog) {
    //noinspection deprecation Acceptable to check the old pin in a reminder on a non-migrated system.
    String pin = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);

    return new AfterTextChanged((Editable s) -> {
      if (s != null && s.toString().replace(" ", "").equals(pin)) {
        dialog.dismiss();
        RegistrationLockReminders.scheduleReminder(context, true);

        Log.i(TAG, "Pin V1 successfully remembered, scheduling a migration to V2");
        ApplicationDependencies.getJobManager().add(new RegistrationPinV2MigrationJob());
      }
    });
  }

  private static TextWatcher getV2PinWatcher(@NonNull Context context, AlertDialog dialog) {
    KbsValues kbsValues    = SignalStore.kbsValues();
    String    localPinHash = kbsValues.getLocalPinHash();

    if (localPinHash == null) throw new AssertionError("No local pin hash set at time of reminder");

    return new AfterTextChanged((Editable s) -> {
      if (s == null) return;
      String pin = s.toString();
      if (TextUtils.isEmpty(pin)) return;
      if (pin.length() < KbsConstants.MINIMUM_POSSIBLE_PIN_LENGTH) return;

      if (PinHashing.verifyLocalPinHash(localPinHash, pin)) {
        dialog.dismiss();
        RegistrationLockReminders.scheduleReminder(context, true);
      }
    });
  }

  @SuppressLint("StaticFieldLeak")
  public static void showRegistrationLockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.RegistrationLockDialog_registration_lock)
                                        .setView(R.layout.registration_lock_dialog_view)
                                        .setPositiveButton(R.string.RegistrationLockDialog_enable, null)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .create();

    dialog.setOnShowListener(created -> {
      Button button = ((AlertDialog) created).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(v -> {
        EditText    pin         = dialog.findViewById(R.id.pin);
        EditText    repeat      = dialog.findViewById(R.id.repeat);
        ProgressBar progressBar = dialog.findViewById(R.id.progress);

        if (pin         == null) throw new AssertionError();
        if (repeat      == null) throw new AssertionError();
        if (progressBar == null) throw new AssertionError();

        String pinValue    = pin.getText().toString().replace(" ", "");
        String repeatValue = repeat.getText().toString().replace(" ", "");

        if (pinValue.length() < KbsConstants.MINIMUM_POSSIBLE_PIN_LENGTH) {
          Toast.makeText(context,
                         context.getString(R.string.RegistrationLockDialog_the_registration_lock_pin_must_be_at_least_d_digits, KbsConstants.MINIMUM_POSSIBLE_PIN_LENGTH),
                         Toast.LENGTH_LONG).show();
          return;
        }

        if (!pinValue.equals(repeatValue)) {
          Toast.makeText(context, R.string.RegistrationLockDialog_the_two_pins_you_entered_do_not_match, Toast.LENGTH_LONG).show();
          return;
        }

        new AsyncTask<Void, Void, Boolean>() {
          @Override
          protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
            button.setEnabled(false);
          }

          @Override
          protected Boolean doInBackground(Void... voids) {
            try {
              Log.i(TAG, "Setting pin on KBS");

              KbsValues                         kbsValues        = SignalStore.kbsValues();
              MasterKey                         masterKey        = kbsValues.getOrCreateMasterKey();
              KeyBackupService                  keyBackupService = ApplicationDependencies.getKeyBackupService();
              KeyBackupService.PinChangeSession pinChangeSession = keyBackupService.newPinChangeSession();
              HashedPin                         hashedPin        = PinHashing.hashPin(pinValue, pinChangeSession);
              RegistrationLockData              kbsData          = pinChangeSession.setPin(hashedPin, masterKey);
              RegistrationLockData              restoredData     = keyBackupService.newRestoreSession(kbsData.getTokenResponse())
                                                                                   .restorePin(hashedPin);

              if (!restoredData.getMasterKey().equals(masterKey)) {
                throw new AssertionError("Failed to set the pin correctly");
              } else {
                Log.i(TAG, "Set and retrieved pin on KBS successfully");
              }

              kbsValues.setRegistrationLockMasterKey(restoredData, PinHashing.localPinHash(pinValue));
              TextSecurePreferences.clearOldRegistrationLockPin(context);
              TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
              TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
              return true;
            } catch (IOException | UnauthenticatedResponseException | KeyBackupServicePinException | KeyBackupSystemNoDataException e) {
              Log.w(TAG, e);
              return false;
            }
          }

          @Override
          protected void onPostExecute(@NonNull Boolean result) {
            button.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            if (result) {
              preference.setChecked(true);
              created.dismiss();
            } else {
              Toast.makeText(context, R.string.RegistrationLockDialog_error_connecting_to_the_service, Toast.LENGTH_LONG).show();
            }
          }
        }.execute();
      });
    });

    dialog.show();
  }

  @SuppressLint("StaticFieldLeak")
  public static void showRegistrationUnlockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference) {

    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.RegistrationLockDialog_disable_registration_lock_pin)
                                        .setView(R.layout.registration_unlock_dialog_view)
                                        .setPositiveButton(R.string.RegistrationLockDialog_disable, null)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .create();

    dialog.setOnShowListener(created -> {
      Button button = ((AlertDialog) created).getButton(AlertDialog.BUTTON_POSITIVE);
      button.setOnClickListener(v -> {
        ProgressBar progressBar = dialog.findViewById(R.id.progress);
        assert progressBar != null;

        new AsyncTask<Void, Void, Boolean>() {
          @Override
          protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(true);
            button.setEnabled(false);
          }

          @Override
          protected Boolean doInBackground(Void... voids) {
            try {
              KbsValues kbsValues = SignalStore.kbsValues();

              if (kbsValues.isV2RegistrationLockEnabled()) {
                Log.i(TAG, "Removing v2 registration lock pin from server");
                TokenResponse currentToken = kbsValues.getRegistrationLockTokenResponse();

                KeyBackupService keyBackupService = ApplicationDependencies.getKeyBackupService();
                keyBackupService.newPinChangeSession(currentToken).removePin();
                kbsValues.clearRegistrationLock();
              }

              // It is possible a migration has not occurred, in this case, we need to remove the old V1 Pin
              if (TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
                Log.i(TAG, "Removing v1 registration lock pin from server");
                ApplicationDependencies.getSignalServiceAccountManager().removeV1Pin();
              }
              TextSecurePreferences.clearOldRegistrationLockPin(context);
              return true;
            } catch (IOException | UnauthenticatedResponseException e) {
              Log.w(TAG, e);
              return false;
            }
          }

          @Override
          protected void onPostExecute(Boolean result) {
            progressBar.setVisibility(View.GONE);
            button.setEnabled(true);

            if (result) {
              preference.setChecked(false);
              created.dismiss();
            } else {
              Toast.makeText(context, R.string.RegistrationLockDialog_error_connecting_to_the_service, Toast.LENGTH_LONG).show();
            }
          }
        }.execute();
      });
    });

    dialog.show();
  }

  private static PinVerifier.Callback getPinWatcherCallback(@NonNull Context context,
                                                            @NonNull AlertDialog dialog,
                                                            @NonNull TextInputLayout inputWrapper)
  {
    return new PinVerifier.Callback() {
      @Override
      public void onPinCorrect() {
        dialog.dismiss();
        RegistrationLockReminders.scheduleReminder(context, true);
      }

      @Override
      public void onPinWrong() {
        inputWrapper.setError(context.getString(R.string.KbsReminderDialog__incorrect_pin_try_again));
      }
    };
  }

  private static final class V1PinVerifier implements PinVerifier {

    private final String pinInPreferences;

    private V1PinVerifier(@NonNull Context context) {
      //noinspection deprecation Acceptable to check the old pin in a reminder on a non-migrated system.
      this.pinInPreferences = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);
    }

    @Override
    public void verifyPin(@Nullable String pin, @NonNull Callback callback) {
      if (pin != null && pin.replace(" ", "").equals(pinInPreferences)) {
        callback.onPinCorrect();

        Log.i(TAG, "Pin V1 successfully remembered, scheduling a migration to V2");
        ApplicationDependencies.getJobManager().add(new RegistrationPinV2MigrationJob());
      } else {
        callback.onPinWrong();
      }
    }
  }

  private static final class V2PinVerifier implements PinVerifier {

    private final String localPinHash;

    V2PinVerifier() {
      localPinHash = SignalStore.kbsValues().getLocalPinHash();

      if (localPinHash == null) throw new AssertionError("No local pin hash set at time of reminder");
    }

    @Override
    public void verifyPin(@Nullable String pin, @NonNull Callback callback) {
      if (pin == null) return;
      if (TextUtils.isEmpty(pin)) return;

      if (pin.length() < KbsConstants.MINIMUM_POSSIBLE_PIN_LENGTH) return;

      if (PinHashing.verifyLocalPinHash(localPinHash, pin)) {
        callback.onPinCorrect();
      } else {
        callback.onPinWrong();
      }
    }
  }

  private interface PinVerifier {

    void verifyPin(@Nullable String pin, @NonNull PinVerifier.Callback callback);

    interface Callback {
      void onPinCorrect();
      void onPinWrong();
    }
  }
}
