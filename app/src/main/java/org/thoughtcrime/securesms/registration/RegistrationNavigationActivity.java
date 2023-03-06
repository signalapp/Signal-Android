package org.thoughtcrime.securesms.registration;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;


public final class RegistrationNavigationActivity extends AppCompatActivity {

  private static final String TAG = Log.tag(RegistrationNavigationActivity.class);

  public static final String RE_REGISTRATION_EXTRA = "re_registration";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private SmsRetrieverReceiver  smsRetrieverReceiver;
  private RegistrationViewModel viewModel;

  public static Intent newIntentForNewRegistration(@NonNull Context context, @Nullable Intent originalIntent) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, false);

    if (originalIntent != null) {
      intent.setData(originalIntent.getData());
    }

    return intent;
  }

  public static Intent newIntentForReRegistration(@NonNull Context context) {
    Intent intent = new Intent(context, RegistrationNavigationActivity.class);
    intent.putExtra(RE_REGISTRATION_EXTRA, true);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    dynamicTheme.onCreate(this);

    super.onCreate(savedInstanceState);
    viewModel = new ViewModelProvider(this, new RegistrationViewModel.Factory(this, isReregister(getIntent()))).get(RegistrationViewModel.class);

    setContentView(R.layout.activity_registration_navigation);
    initializeChallengeListener();

    if (getIntent() != null && getIntent().getData() != null) {
      CommunicationActions.handlePotentialProxyLinkUrl(this, getIntent().getDataString());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    if (intent.getData() != null) {
      CommunicationActions.handlePotentialProxyLinkUrl(this, intent.getDataString());
    }

    viewModel.setIsReregister(isReregister(intent));
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    shutdownChallengeListener();
  }

  private boolean isReregister(@NonNull Intent intent) {
    return intent.getBooleanExtra(RE_REGISTRATION_EXTRA, false);
  }

  private void initializeChallengeListener() {
    smsRetrieverReceiver = new SmsRetrieverReceiver(getApplication());
    smsRetrieverReceiver.registerReceiver();
  }

  private void shutdownChallengeListener() {
    if (smsRetrieverReceiver != null) {
      smsRetrieverReceiver.unregisterReceiver();
      smsRetrieverReceiver = null;
    }
  }
}
