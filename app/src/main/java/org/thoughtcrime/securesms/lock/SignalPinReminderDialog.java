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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity;
import org.thoughtcrime.securesms.lock.v2.SvrConstants;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;

import java.util.Objects;

public final class SignalPinReminderDialog {

  private static final String TAG = Log.tag(SignalPinReminderDialog.class);

  public static void show(@NonNull Context context, @NonNull Launcher launcher, @NonNull Callback mainCallback) {
    if (!SignalStore.svr().hasPin()) {
      throw new AssertionError("Must have a PIN!");
    }

    Log.i(TAG, "Showing PIN reminder dialog.");

    AlertDialog dialog = new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Signal_MaterialAlertDialog_Wide)
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
    TextView reminder    = (TextView) DialogCompat.requireViewById(dialog, R.id.kbs_reminder_body);
    View     skip        = DialogCompat.requireViewById(dialog, R.id.skip);
    View     submit      = DialogCompat.requireViewById(dialog, R.id.submit);

    SpannableString reminderText = new SpannableString(context.getString(R.string.KbsReminderDialog__to_help_you_memorize_your_pin));
    SpannableString forgotText   = new SpannableString(context.getString(R.string.KbsReminderDialog__forgot_pin));

    ViewUtil.focusAndShowKeyboard(pinEditText);
    ViewCompat.setAutofillHints(pinEditText, HintConstants.AUTOFILL_HINT_PASSWORD);

    switch (SignalStore.pin().getKeyboardType()) {
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
        launcher.launch(CreateSvrPinActivity.getIntentForPinChangeFromForgotPin(context), CreateSvrPinActivity.REQUEST_NEW_PIN);
      }
    };

    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    PinVerifier.Callback callback = getPinWatcherCallback(context, dialog, pinEditText, pinStatus, mainCallback);
    PinVerifier          verifier = new V2PinVerifier();

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

      private final String localHash = Objects.requireNonNull(SignalStore.svr().getLocalPinHash());

      @Override
      public void onTextChanged(String text) {
        if (text.length() >= SvrConstants.MINIMUM_PIN_LENGTH) {
          submit.setEnabled(true);

          if (PinHashUtil.verifyLocalPinHash(localHash, text)) {
            dialog.dismiss();
            mainCallback.onReminderCompleted(text, callback.hadWrongGuess());
          }
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
      public void onPinCorrect(@NonNull String pin) {
        Log.i(TAG, "Correct PIN entry.");
        dialog.dismiss();
        mainCallback.onReminderCompleted(pin, hadWrongGuess);
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

  private static final class V2PinVerifier implements PinVerifier {

    private final String localPinHash;

    V2PinVerifier() {
      localPinHash = SignalStore.svr().getLocalPinHash();

      if (localPinHash == null) throw new AssertionError("No local pin hash set at time of reminder");
    }

    @Override
    public void verifyPin(@Nullable String pin, @NonNull Callback callback) {
      if (pin == null) return;
      if (TextUtils.isEmpty(pin)) return;

      if (pin.length() < SvrConstants.MINIMUM_PIN_LENGTH) return;

      if (PinHashUtil.verifyLocalPinHash(localPinHash, pin)) {
        callback.onPinCorrect(pin);
      } else {
        callback.onPinWrong();
      }
    }
  }

  private interface PinVerifier {

    void verifyPin(@Nullable String pin, @NonNull PinVerifier.Callback callback);

    interface Callback {
      void onPinCorrect(@NonNull String pin);
      void onPinWrong();
      boolean hadWrongGuess();
    }
  }

  public interface Launcher {
    void launch(@NonNull Intent intent, int requestCode);
  }

  public interface Callback {
    void onReminderDismissed(boolean includedFailure);
    void onReminderCompleted(@NonNull String pin, boolean includedFailure);
  }
}
