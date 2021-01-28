package org.thoughtcrime.securesms.registration.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProviders;

import com.dd.CircularProgressButton;

import org.signal.core.util.TranslationDetection;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.SpanUtil;

import java.util.Locale;

import static org.thoughtcrime.securesms.registration.RegistrationNavigationActivity.RE_REGISTRATION_EXTRA;

abstract class BaseRegistrationFragment extends LoggingFragment {

  private static final String TAG = Log.tag(BaseRegistrationFragment.class);

  private RegistrationViewModel model;

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    this.model = getRegistrationViewModel(requireActivity());
  }

  protected @NonNull RegistrationViewModel getModel() {
    return model;
  }

  protected boolean isReregister() {
    Activity activity = getActivity();

    if (activity == null) {
      return false;
    }

    return activity.getIntent().getBooleanExtra(RE_REGISTRATION_EXTRA, false);
  }

  protected static RegistrationViewModel getRegistrationViewModel(@NonNull FragmentActivity activity) {
    SavedStateViewModelFactory savedStateViewModelFactory = new SavedStateViewModelFactory(activity.getApplication(), activity);

    return ViewModelProviders.of(activity, savedStateViewModelFactory).get(RegistrationViewModel.class);
  }

  protected static void setSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setClickable(false);
      button.setIndeterminateProgressMode(true);
      button.setProgress(50);
    }
  }

  protected static void cancelSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setProgress(0);
      button.setIndeterminateProgressMode(false);
      button.setClickable(true);
    }
  }

  protected static void hideKeyboard(@NonNull Context context, @NonNull View view) {
    InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  /**
   * Sets view up to allow log submitting after multiple taps.
   */
  protected static void setDebugLogSubmitMultiTapView(@Nullable View view) {
    if (view == null) return;

    view.setOnClickListener(new View.OnClickListener() {

      private static final int DEBUG_TAP_TARGET   = 8;
      private static final int DEBUG_TAP_ANNOUNCE = 4;

      private int debugTapCounter;

      @Override
      public void onClick(View v) {
        Context context = v.getContext();

        debugTapCounter++;

        if (debugTapCounter >= DEBUG_TAP_TARGET) {
          context.startActivity(new Intent(context, SubmitDebugLogActivity.class));
        } else if (debugTapCounter >= DEBUG_TAP_ANNOUNCE) {
          int remaining = DEBUG_TAP_TARGET - debugTapCounter;

          Toast.makeText(context, context.getResources().getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining), Toast.LENGTH_SHORT).show();
        }
      }
    });
  }

  /**
   * Presents a prompt for the user to confirm their number as long as it can be shown in one of their device languages.
   */
  protected final void showConfirmNumberDialogIfTranslated(@NonNull Context context,
                                                           @StringRes int firstMessageLine,
                                                           @NonNull String e164number,
                                                           @NonNull Runnable onConfirmed,
                                                           @NonNull Runnable onEditNumber)
  {
    TranslationDetection translationDetection = new TranslationDetection(context);

    if (translationDetection.textExistsInUsersLanguage(firstMessageLine) &&
        translationDetection.textExistsInUsersLanguage(R.string.RegistrationActivity_is_your_phone_number_above_correct) &&
        translationDetection.textExistsInUsersLanguage(R.string.RegistrationActivity_edit_number))
    {
      CharSequence message = new SpannableStringBuilder().append(context.getString(firstMessageLine))
                                                         .append("\n\n")
                                                         .append(SpanUtil.bold(PhoneNumberFormatter.prettyPrint(e164number)))
                                                         .append("\n\n")
                                                         .append(context.getString(R.string.RegistrationActivity_is_your_phone_number_above_correct));

      Log.i(TAG, "Showing confirm number dialog (" + context.getString(firstMessageLine) + ")");
      new AlertDialog.Builder(context)
                     .setMessage(message)
                     .setPositiveButton(android.R.string.ok,
                                        (a, b) -> {
                                          Log.i(TAG, "Number confirmed");
                                          onConfirmed.run();
                                        })
                     .setNegativeButton(R.string.RegistrationActivity_edit_number,
                                        (a, b) -> {
                                          Log.i(TAG, "User requested edit number from confirm dialog");
                                          onEditNumber.run();
                                        })
                     .show();
    } else {
     Log.i(TAG, "Confirm number dialog not translated in " + Locale.getDefault() + " skipping");
     onConfirmed.run();
    }
  }
}
