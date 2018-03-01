package org.thoughtcrime.securesms.lock;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import java.io.IOException;

public class RegistrationLockDialog {

  private static final String TAG = RegistrationLockDialog.class.getSimpleName();

  public static void showReminderIfNecessary(@NonNull Context context) {
    if (!RegistrationLockReminders.needsReminder(context)) return;

    AlertDialog dialog      = new AlertDialog.Builder(context, R.style.RationaleDialog)
                                             .setView(R.layout.registration_lock_reminder_view)
                                             .setCancelable(true)
                                             .setOnCancelListener(d -> RegistrationLockReminders.scheduleReminder(context, false))
                                             .create();

    WindowManager  windowManager = ServiceUtil.getWindowManager(context);
    Display        display       = windowManager.getDefaultDisplay();
    DisplayMetrics metrics       = new DisplayMetrics();
    display.getMetrics(metrics);

    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    dialog.getWindow().setLayout((int)(metrics.widthPixels * .75), ViewGroup.LayoutParams.WRAP_CONTENT);
    dialog.show();

    EditText    pinEditText = dialog.findViewById(R.id.pin);
    TextView    reminder    = dialog.findViewById(R.id.reminder);

    assert pinEditText != null;
    assert reminder != null;

    SpannableString reminderText = new SpannableString(context.getString(R.string.RegistrationLockDialog_registration_lock_is_enabled_for_your_phone_number));
    SpannableString forgotText   = new SpannableString(context.getString(R.string.RegistrationLockDialog_i_forgot_my_pin));

    ClickableSpan clickableSpan = new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        dialog.dismiss();
        new AlertDialog.Builder(context).setTitle(R.string.RegistrationLockDialog_forgotten_pin)
                                        .setMessage(R.string.RegistrationLockDialog_registration_lock_helps_protect_your_phone_number_from_unauthorized_registration_attempts)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .create()
                                        .show();
      }
    };

    forgotText.setSpan(clickableSpan, 0, forgotText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    reminder.setText(new SpannableStringBuilder(reminderText).append(" ").append(forgotText));
    reminder.setMovementMethod(LinkMovementMethod.getInstance());

    pinEditText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        if (s != null && s.toString().replace(" ", "").equals(TextSecurePreferences.getRegistrationLockPin(context))) {
          dialog.dismiss();
          RegistrationLockReminders.scheduleReminder(context, true);
        }
      }
    });

  }

  @SuppressLint("StaticFieldLeak")
  public static void showRegistrationLockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference, @NonNull SignalServiceAccountManager accountManager) {
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

        assert pin != null;
        assert repeat != null;
        assert progressBar != null;

        String pinValue    = pin.getText().toString().replace(" ", "");
        String repeatValue = repeat.getText().toString().replace(" ", "");

        if (pinValue.length() < 4) {
          Toast.makeText(context, R.string.RegistrationLockDialog_the_registration_lock_pin_must_be_at_least_four_digits, Toast.LENGTH_LONG).show();
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
              accountManager.setPin(Optional.of(pinValue));
              TextSecurePreferences.setRegistrationLockPin(context, pinValue);
              TextSecurePreferences.setRegistrationLockLastReminderTime(context, System.currentTimeMillis());
              TextSecurePreferences.setRegistrationLockNextReminderInterval(context, RegistrationLockReminders.INITIAL_INTERVAL);
              return true;
            } catch (IOException e) {
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
  public static void showRegistrationUnlockPrompt(@NonNull Context context, @NonNull SwitchPreferenceCompat preference, @NonNull SignalServiceAccountManager accountManager) {
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
              accountManager.setPin(Optional.absent());
              return true;
            } catch (IOException e) {
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

}
