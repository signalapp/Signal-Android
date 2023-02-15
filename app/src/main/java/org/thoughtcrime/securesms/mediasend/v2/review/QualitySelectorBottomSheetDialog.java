package org.thoughtcrime.securesms.mediasend.v2.review;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionState;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.views.CheckedLinearLayout;

/**
 * Dialog for selecting media quality, tightly coupled with {@link MediaSelectionViewModel}.
 */
public final class QualitySelectorBottomSheetDialog extends BottomSheetDialogFragment {

  private MediaSelectionViewModel viewModel;
  private CheckedLinearLayout     standard;
  private CheckedLinearLayout     high;

  public static void show(@NonNull FragmentManager manager) {
    QualitySelectorBottomSheetDialog fragment = new QualitySelectorBottomSheetDialog();

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Signal_RoundedBottomSheet);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(inflater.getContext(), R.style.TextSecure_DarkTheme);
    LayoutInflater      themedInflater      = LayoutInflater.from(contextThemeWrapper);

    return themedInflater.inflate(R.layout.quality_selector_dialog, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
    standard = view.findViewById(R.id.quality_selector_dialog_standard);
    high     = view.findViewById(R.id.quality_selector_dialog_high);

    View.OnClickListener listener = v -> {
      select(v);
      view.postDelayed(this::dismissAllowingStateLoss, 250);
    };

    standard.setOnClickListener(listener);
    high.setOnClickListener(listener);

    viewModel = new ViewModelProvider(requireActivity()).get(MediaSelectionViewModel.class);
    viewModel.getState().observe(getViewLifecycleOwner(), this::updateQuality);
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  private void updateQuality(@NonNull MediaSelectionState selectionState) {
    select(selectionState.getQuality() == SentMediaQuality.STANDARD ? standard : high);
  }

  private void select(@NonNull View view) {
    standard.setChecked(view == standard);
    high.setChecked(view == high);
    viewModel.setSentMediaQuality(standard == view ? SentMediaQuality.STANDARD : SentMediaQuality.HIGH);
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }
}
