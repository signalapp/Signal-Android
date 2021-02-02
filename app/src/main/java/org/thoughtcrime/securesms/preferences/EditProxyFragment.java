package org.thoughtcrime.securesms.preferences;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.PipeConnectivityListener;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

public class EditProxyFragment extends Fragment {

  private SwitchCompat           proxySwitch;
  private EditText               proxyText;
  private TextView               proxyTitle;
  private TextView               proxyStatus;
  private View                   shareButton;
  private CircularProgressButton saveButton;
  private EditProxyViewModel     viewModel;

  public static EditProxyFragment newInstance() {
    return new EditProxyFragment();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.edit_proxy_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.proxySwitch = view.findViewById(R.id.edit_proxy_switch);
    this.proxyTitle  = view.findViewById(R.id.edit_proxy_address_title);
    this.proxyText   = view.findViewById(R.id.edit_proxy_host);
    this.proxyStatus = view.findViewById(R.id.edit_proxy_status);
    this.saveButton  = view.findViewById(R.id.edit_proxy_save);
    this.shareButton = view.findViewById(R.id.edit_proxy_share);

    this.proxyText.setText(Optional.fromNullable(SignalStore.proxy().getProxy()).transform(SignalProxy::getHost).or(""));
    this.proxySwitch.setChecked(SignalStore.proxy().isProxyEnabled());

    initViewModel();

    saveButton.setOnClickListener(v -> onSaveClicked());
    shareButton.setOnClickListener(v -> onShareClicked());
    proxySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.onToggleProxy(isChecked));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) requireActivity()).requireSupportActionBar().setTitle(R.string.preferences_use_proxy);
  }

  private void initViewModel() {
    viewModel = ViewModelProviders.of(this).get(EditProxyViewModel.class);

    viewModel.getUiState().observe(getViewLifecycleOwner(), this::presentUiState);
    viewModel.getProxyState().observe(getViewLifecycleOwner(), this::presentProxyState);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvent);
    viewModel.getSaveState().observe(getViewLifecycleOwner(), this::presentSaveState);
  }

  private void presentUiState(@NonNull EditProxyViewModel.UiState uiState) {
    switch (uiState) {
      case ALL_ENABLED:
        proxyText.setEnabled(true);
        proxyText.setAlpha(1);
        saveButton.setEnabled(true);
        saveButton.setAlpha(1);
        shareButton.setEnabled(true);
        shareButton.setAlpha(1);
        proxyTitle.setAlpha(1);
        proxyStatus.setVisibility(View.VISIBLE);
        break;
      case ALL_DISABLED:
        proxyText.setEnabled(false);
        proxyText.setAlpha(0.5f);
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.5f);
        shareButton.setEnabled(false);
        shareButton.setAlpha(0.5f);
        proxyTitle.setAlpha(0.5f);
        proxyStatus.setVisibility(View.GONE);
        break;
    }
  }

  private void presentProxyState(@NonNull PipeConnectivityListener.State proxyState) {
    switch (proxyState) {
      case DISCONNECTED:
      case CONNECTING:
        proxyStatus.setText(R.string.preferences_connecting_to_proxy);
        proxyStatus.setTextColor(getResources().getColor(R.color.signal_text_secondary));
        break;
      case CONNECTED:
        proxyStatus.setText(R.string.preferences_connected_to_proxy);
        proxyStatus.setTextColor(getResources().getColor(R.color.signal_accent_green));
        break;
      case FAILURE:
        proxyStatus.setText(R.string.preferences_connection_failed);
        proxyStatus.setTextColor(getResources().getColor(R.color.signal_alert_primary));
        break;
    }
  }

  private void presentEvent(@NonNull EditProxyViewModel.Event event) {
    switch (event) {
      case PROXY_SUCCESS:
        proxyStatus.setVisibility(View.VISIBLE);
        proxyText.setText(Optional.fromNullable(SignalStore.proxy().getProxy()).transform(SignalProxy::getHost).or(""));
        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.preferences_success)
                       .setMessage(R.string.preferences_you_are_connected_to_the_proxy)
                       .setPositiveButton(android.R.string.ok, (d, i) -> d.dismiss())
                       .show();
        break;
      case PROXY_FAILURE:
        proxyStatus.setVisibility(View.GONE);
        proxyText.setText(Optional.fromNullable(SignalStore.proxy().getProxy()).transform(SignalProxy::getHost).or(""));
        new AlertDialog.Builder(requireContext())
                       .setTitle(R.string.preferences_failed_to_connect)
                       .setMessage(R.string.preferences_couldnt_connect_to_the_proxy)
                       .setPositiveButton(android.R.string.ok, (d, i) -> d.dismiss())
                       .show();
        break;
    }
  }

  private void presentSaveState(@NonNull EditProxyViewModel.SaveState state) {
    switch (state) {
      case IDLE:
        saveButton.setClickable(true);
        saveButton.setIndeterminateProgressMode(false);
        saveButton.setProgress(0);
        break;
      case IN_PROGRESS:
        saveButton.setClickable(false);
        saveButton.setIndeterminateProgressMode(true);
        saveButton.setProgress(50);
        break;
    }
  }

  private void onSaveClicked() {
    viewModel.onSaveClicked(proxyText.getText().toString());
  }

  private void onShareClicked() {
    String host = proxyText.getText().toString();
    ShareCompat.IntentBuilder.from(requireActivity())
                             .setText(host)
                             .setType("text/plain")
                             .startChooser();
  }
}
