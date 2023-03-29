package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.qr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.qr.QrCodeUtil;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    Bundle                            arguments = requireArguments();
    GroupId.V2                        groupId   = GroupId.parseOrThrow(Objects.requireNonNull(arguments.getString(ARG_GROUP_ID))).requireV2();
    GroupLinkShareQrViewModel.Factory factory   = new GroupLinkShareQrViewModel.Factory(groupId);

    viewModel = new ViewModelProvider(this, factory).get(GroupLinkShareQrViewModel.class);
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

    // Restricted to API26 because of MemoryFileUtil not supporting lower API levels well
    if (Build.VERSION.SDK_INT >= 26) {
      shareCodeButton.setVisibility(View.VISIBLE);

      shareCodeButton.setOnClickListener(v -> {
        Uri shareUri;

        try {
          shareUri = createTemporaryPng(url);
        } catch (IOException e) {
          Log.w(TAG, e);
          return;
        }

        Intent intent = ShareCompat.IntentBuilder.from(requireActivity())
                                                 .setType("image/png")
                                                 .setStream(shareUri)
                                                 .createChooserIntent()
                                                 .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        requireContext().startActivity(intent);
      });
    } else {
      shareCodeButton.setVisibility(View.GONE);
    }
  }

  private static Uri createTemporaryPng(@Nullable String url) throws IOException {
    Bitmap qrBitmap = QrCodeUtil.create(url, Color.BLACK, Color.WHITE);

    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
      byteArrayOutputStream.flush();

      byte[] bytes = byteArrayOutputStream.toByteArray();

      return BlobProvider.getInstance()
                         .forData(bytes)
                         .withMimeType("image/png")
                         .withFileName("SignalGroupQr.png")
                         .createForSingleSessionInMemory();
    } finally {
      qrBitmap.recycle();
    }
  }
}
