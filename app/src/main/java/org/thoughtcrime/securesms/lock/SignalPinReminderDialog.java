package org.thoughtcrime.securesms.lock;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.autofill.HintConstants;
import androidx.core.app.DialogCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.textfield.TextInputLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;

public final class SignalPinReminderDialog {

  private static final String TAG = Log.tag(SignalPinReminderDialog.class);

  public static void show(@NonNull Context context, @NonNull Launcher launcher, @NonNull Callback mainCallback) {
    Log.i(TAG, "Showing PIN reminder dialog.");

    AlertDialog dialog = new AlertDialog.Builder(context, ThemeUtil.isDarkTheme(context) ? R.style.Theme_Signal_AlertDialog_Dark_Cornered_ColoredAccent : R.style.Theme_Signal_AlertDialog_Light_Cornered_ColoredAccent)
                                        .setView(R.layout.kbs_pin_reminder_view)
                                        .setCancelable(false)
                                        .setOnCancelListener(d -> RegistrationLockReminders.scheduleReminder(context, false))
                                        .create();

    WindowManager  windowManager = ServiceUtil.getWindowManager(context);
    Display        display       = windowManager.getDefaultDisplay();
    DisplayMetrics metrics       = new DisplayMetrics();
    display.getMetrics(metrics);

    dialog.show();
    dialog.getWindow().setLayout((int)(metrics.widthPixels * .80), ViewGroup.LayoutParams.WRAP_CONTENT);

    EditText pinEditText = (EditText) DialogCompat.requireViewById(dialog, R.id.pin);
    TextView pinStatus   = (TextView) DialogCompat.requireViewById(dialog, R.id.pin_status);
    TextView reminder    = (TextView) DialogCompat.requireViewById(dialog, R.id.reminder);
    View     skip        = DialogCompat.requireViewById(dialog, R.id.skip);
    View     submit      = DialogCompat.requireViewById(dialog, R.id.submit);

    SpannableString reminderText = new SpannableString(context.getString(R.string.KbsReminderDialog__to_help_you_memorize_your_pin));
    SpannableString forgotText   = new SpannableString(context.getString(R.string.KbsReminderDialog__forgot_pin));

    pinEditText.post(() -> {
      if (pinEditText.requestFocus()) {
        ServiceUtil.getInputMethodManager(pinEditText.getContext()).showSoftInput(pinEditText, 0);
      }
    });
    ViewCompat.setAutofillHints(pinEditText, HintConstants.AUTOFILL_HINT_PASSWORD);

    switch (SignalStore.pinValues().getKeyboardType()) {
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
        launcher.launch(CreateKbsPinActivity.getIntentForPinChangeFromForgotPin(context), CreateKbsPinActivity.REQUEST_NEW_PIN);
      }
    };

    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    PinVerifier.Callback callback = getPinWatcherCallback(context, dialog, pinEditText, pinStatus, mainCallback);
    PinVerifier          verifier = SignalStore.kbsValues().isV2RegistrationLockEnabled()
                                    ? new V2PinVerifier()
                                    : new V1PinVerifier(context);

    skip.setOnClickListener(v -> {
      dialog.dismiss();
      mainCallback.onReminderDismissed(callback.hadWrongGuess());
    });

    submit.setEnabled(false);
    submit.setOnClickListener(v -> {
      Editable pinEditable = pinEditText.getText();

      verifier.verifyPin(pinEditable == null ? null : pinEditable.toString(), callback);
    });

    pinEditText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        if (text.length() >= KbsConstants.MINIMUM_PIN_LENGTH) {
          submit.setEnabled(true);
        } else {
          submit.setEnabled(false);
        }
      }
    });
  }

  private static PinVerifier.Callback getPinWatcherCallback(@NonNull Context context,
                                                            @NonNull AlertDialog dialog,
                                                            @NonNull EditText inputText,
                                                            @NonNull TextView statusText,
                                                            @NonNull Callback mainCallback)
  {
    return new PinVerifier.Callback() {
      boolean hadWrongGuess = false;

      @Override
      public void onPinCorrect() {
        Log.i(TAG, "Correct PIN entry.");
        dialog.dismiss();
        mainCallback.onReminderCompleted(hadWrongGuess);
      }

      @Override
      public void onPinWrong() {
        Log.i(TAG, "Incorrect PIN entry.");
        hadWrongGuess = true;
        inputText.getText().clear();
        statusText.setText(context.getString(R.string.KbsReminderDialog__incorrect_pin_try_again));
      }

      @Override
      public boolean hadWrongGuess() {
        return hadWrongGuess;
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

      if (pin.length() < KbsConstants.MINIMUM_PIN_LENGTH) return;

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
      boolean hadWrongGuess();
    }
  }

  public interface Launcher {
    void launch(@NonNull Intent intent, int requestCode);
  }

  public interface Callback {
    void onReminderDismissed(boolean includedFailure);
    void onReminderCompleted(boolean includedFailure);
  }
}
