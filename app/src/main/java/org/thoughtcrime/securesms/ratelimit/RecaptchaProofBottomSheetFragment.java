package org.thoughtcrime.securesms.ratelimit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

/**
 * A bottom sheet to be shown when we need to prompt the user to fill out a reCAPTCHA.
 */
public final class RecaptchaProofBottomSheetFragment extends BottomSheetDialogFragment {

  private static final String TAG = Log.tag(RecaptchaProofBottomSheetFragment.class);

  public static void show(@NonNull FragmentManager manager) {
    new RecaptchaProofBottomSheetFragment().show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Signal_DayNight_BottomSheet_Rounded);
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.recaptcha_required_bottom_sheet, container, false);

    view.findViewById(R.id.recaptcha_sheet_ok_button).setOnClickListener(v -> {
      dismissAllowingStateLoss();
      startActivity(RecaptchaProofActivity.getIntent(requireContext()));
    });

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    Log.i(TAG, "Showing reCAPTCHA proof bottom sheet.");

    if (manager.findFragmentByTag(tag) == null) {
      BottomSheetUtil.show(manager, tag, this);
    } else {
      Log.i(TAG, "Ignoring repeat show.");
    }
  }
}
