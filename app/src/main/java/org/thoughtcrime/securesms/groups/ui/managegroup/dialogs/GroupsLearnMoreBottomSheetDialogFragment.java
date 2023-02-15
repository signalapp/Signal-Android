package org.thoughtcrime.securesms.groups.ui.managegroup.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

public final class GroupsLearnMoreBottomSheetDialogFragment extends BottomSheetDialogFragment {

  public static void show(@NonNull FragmentManager manager) {
    new GroupsLearnMoreBottomSheetDialogFragment().show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL,
             ThemeUtil.isDarkTheme(requireContext()) ? R.style.Theme_Signal_RoundedBottomSheet
                                                     : R.style.Theme_Signal_RoundedBottomSheet_Light);

    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.groups_learn_more_bottom_sheet, container, false);

    view.findViewById(R.id.lbs_ok_button).setOnClickListener(v -> dismiss());

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }
}
