package org.thoughtcrime.securesms.proxy;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.dd.CircularProgressButton;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.preferences.EditProxyViewModel;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

/**
 * A bottom sheet shown in response to a deep link. Allows a user to set a proxy.
 */
public final class ProxyBottomSheetFragment extends BottomSheetDialogFragment {

  private static final String TAG = Log.tag(ProxyBottomSheetFragment.class);

  private static final String ARG_PROXY_LINK = "proxy_link";

  private TextView               proxyText;
  private View                   cancelButton;
  private CircularProgressButton useProxyButton;
  private EditProxyViewModel     viewModel;

  public static void showForProxy(@NonNull FragmentManager manager, @NonNull String proxyLink) {
    ProxyBottomSheetFragment fragment = new ProxyBottomSheetFragment();

    Bundle args = new Bundle();
    args.putString(ARG_PROXY_LINK, proxyLink);
    fragment.setArguments(args);

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
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
    return inflater.inflate(R.layout.proxy_bottom_sheet, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.proxyText      = view.findViewById(R.id.proxy_sheet_host);
    this.useProxyButton = view.findViewById(R.id.proxy_sheet_use_proxy);
    this.cancelButton   = view.findViewById(R.id.proxy_sheet_cancel);

    String host = getArguments().getString(ARG_PROXY_LINK);
    proxyText.setText(host);

    initViewModel();

    useProxyButton.setOnClickListener(v -> viewModel.onSaveClicked(host));
    cancelButton.setOnClickListener(v -> dismiss());
  }

  private void initViewModel() {
    this.viewModel = ViewModelProviders.of(this).get(EditProxyViewModel.class);

    viewModel.getSaveState().observe(getViewLifecycleOwner(), this::presentSaveState);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvents);
  }

  private void presentSaveState(@NonNull EditProxyViewModel.SaveState state) {
    switch (state) {
      case IDLE:
        useProxyButton.setClickable(true);
        useProxyButton.setIndeterminateProgressMode(false);
        useProxyButton.setProgress(0);
        break;
      case IN_PROGRESS:
        useProxyButton.setClickable(false);
        useProxyButton.setIndeterminateProgressMode(true);
        useProxyButton.setProgress(50);
        break;
    }
  }

  private void presentEvents(@NonNull EditProxyViewModel.Event event) {
    switch (event) {
      case PROXY_SUCCESS:
        Toast.makeText(requireContext(), R.string.ProxyBottomSheetFragment_successfully_connected_to_proxy, Toast.LENGTH_LONG).show();
        dismiss();
        break;
      case PROXY_FAILURE:
        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.preferences_failed_to_connect)
                       .setMessage(R.string.preferences_couldnt_connect_to_the_proxy)
                       .setPositiveButton(android.R.string.ok, (d, i) -> d.dismiss())
                       .show();
        dismiss();
        break;
    }
  }
}
