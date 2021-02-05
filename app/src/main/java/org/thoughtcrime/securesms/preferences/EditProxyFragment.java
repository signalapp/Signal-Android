package org.thoughtcrime.securesms.preferences;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.PipeConnectivityListener;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
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

    proxyText.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        onProxyTextChanged(text);
      }
    });

    this.proxyText.setText(Optional.fromNullable(SignalStore.proxy().getProxy()).transform(SignalProxy::getHost).or(""));
    this.proxySwitch.setChecked(SignalStore.proxy().isProxyEnabled());

    initViewModel();

    saveButton.setOnClickListener(v -> onSaveClicked());
    shareButton.setOnClickListener(v -> onShareClicked());
    proxySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.onToggleProxy(isChecked));

    LearnMoreTextView description = view.findViewById(R.id.edit_proxy_switch_title_description);
    description.setLearnMoreVisible(true);
    description.setOnLinkClickListener(v -> CommunicationActions.openBrowserLink(requireContext(), "https://support.signal.org/hc/articles/360056052052"));

    requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(R.string.preferences_use_proxy);
    SignalProxyUtil.startListeningToWebsocket();
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
        proxyTitle.setAlpha(1);
        onProxyTextChanged(proxyText.getText().toString());
        break;
      case ALL_DISABLED:
        proxyText.setEnabled(false);
        proxyText.setAlpha(0.5f);
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.5f);
        shareButton.setEnabled(false);
        shareButton.setAlpha(0.5f);
        proxyTitle.setAlpha(0.5f);
        proxyStatus.setVisibility(View.INVISIBLE);
        break;
    }
  }

  private void presentProxyState(@NonNull PipeConnectivityListener.State proxyState) {
    if (SignalStore.proxy().getProxy() != null) {
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
    } else {
      proxyStatus.setText("");
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
                       .setPositiveButton(android.R.string.ok, (d, i) -> {
                         requireActivity().onBackPressed();
                         d.dismiss();
                       })
                       .show();
        break;
      case PROXY_FAILURE:
        proxyStatus.setVisibility(View.INVISIBLE);
        proxyText.setText(Optional.fromNullable(SignalStore.proxy().getProxy()).transform(SignalProxy::getHost).or(""));
        ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(proxyText);
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
    String link = SignalProxyUtil.generateProxyUrl(proxyText.getText().toString());
    ShareCompat.IntentBuilder.from(requireActivity())
                             .setText(link)
                             .setType("text/plain")
                             .startChooser();
  }

  private void onProxyTextChanged(@NonNull String text) {
    if (Util.isEmpty(text)) {
      saveButton.setEnabled(false);
      saveButton.setAlpha(0.5f);
      shareButton.setEnabled(false);
      shareButton.setAlpha(0.5f);
      proxyStatus.setVisibility(View.INVISIBLE);
    } else {
      saveButton.setEnabled(true);
      saveButton.setAlpha(1);
      shareButton.setEnabled(true);
      shareButton.setAlpha(1);

      String trueHost = SignalProxyUtil.convertUserEnteredAddressToHost(proxyText.getText().toString());
      if (SignalStore.proxy().isProxyEnabled() && trueHost.equals(SignalStore.proxy().getProxyHost())) {
        proxyStatus.setVisibility(View.VISIBLE);
      } else {
        proxyStatus.setVisibility(View.INVISIBLE);
      }
    }
  }
}
