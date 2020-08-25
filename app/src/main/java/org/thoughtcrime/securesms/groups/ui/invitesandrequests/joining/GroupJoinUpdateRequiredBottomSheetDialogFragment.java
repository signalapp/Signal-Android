package org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

public final class GroupJoinUpdateRequiredBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private TextView groupJoinTitle;
  private TextView groupJoinExplain;
  private Button   groupJoinButton;

  public static void show(@NonNull FragmentManager manager) {
    new GroupJoinUpdateRequiredBottomSheetDialogFragment().show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
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
    View view = inflater.inflate(R.layout.group_join_update_needed_bottom_sheet, container, false);

    groupJoinTitle   = view.findViewById(R.id.group_join_update_title);
    groupJoinButton  = view.findViewById(R.id.group_join_update_button);
    groupJoinExplain = view.findViewById(R.id.group_join_update_explain);

    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    switch (FeatureFlags.clientLocalGroupJoinStatus()) {
      case COMING_SOON:
        groupJoinTitle.setText(R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_group_links_coming_soon);
        groupJoinExplain.setText(R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_coming_soon);
        groupJoinButton.setText(android.R.string.ok);
        groupJoinButton.setOnClickListener(v -> dismiss());
        break;
      case UPDATE_TO_JOIN:
      case LOCAL_CAN_JOIN:
        groupJoinTitle.setText(R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_update_signal_to_use_group_links);
        groupJoinExplain.setText(R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_update_message);
        groupJoinButton.setText(R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_update_signal);
        groupJoinButton.setOnClickListener(v -> {
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext());
          dismiss();
        });
        break;
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }
}
