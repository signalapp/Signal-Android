package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.qr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.util.Objects;

public class GroupLinkShareQrDialogFragment extends DialogFragment {

  private static final String TAG = Log.tag(GroupLinkShareQrDialogFragment.class);

  private static final String ARG_GROUP_ID = "group_id";

  private GroupLinkShareQrViewModel viewModel;
  private QrView                    qrImageView;
  private View                      shareCodeButton;

  public static void show(@NonNull FragmentManager manager, @NonNull GroupId.V2 groupId) {
    DialogFragment fragment = new GroupLinkShareQrDialogFragment();
    Bundle         args     = new Bundle();

    args.putString(ARG_GROUP_ID, groupId.toString());
    fragment.setArguments(args);

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setStyle(STYLE_NO_FRAME, ThemeUtil.isDarkTheme(requireActivity()) ? R.style.TextSecure_DarkTheme
                                                                      : R.style.TextSecure_LightTheme);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.group_link_share_qr_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModel();
    initializeViews(view);
  }

  private void initializeViewModel() {
    Bundle                            arguments  = requireArguments();
    GroupId.V2                        groupId    = GroupId.parseOrThrow(Objects.requireNonNull(arguments.getString(ARG_GROUP_ID))).requireV2();
    GroupLinkShareQrViewModel.Factory factory    = new GroupLinkShareQrViewModel.Factory(groupId);

    viewModel = ViewModelProviders.of(this, factory).get(GroupLinkShareQrViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    Toolbar toolbar = view.findViewById(R.id.group_link_share_qr_toolbar);

    qrImageView     = view.findViewById(R.id.group_link_share_qr_image);
    shareCodeButton = view.findViewById(R.id.group_link_share_code_button);

    toolbar.setNavigationOnClickListener(v -> dismissAllowingStateLoss());

    viewModel.getQrUrl().observe(getViewLifecycleOwner(), this::presentUrl);
  }

  private void presentUrl(@Nullable String url) {
    qrImageView.setQrText(url);

    shareCodeButton.setOnClickListener(v -> {
      // TODO [Alan] GV2 Allow qr image share
    });
  }
}
