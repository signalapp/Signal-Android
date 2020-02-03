package org.thoughtcrime.securesms.lock.v2;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.PassphrasePromptActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class CreateKbsPinActivity extends BaseActionBarActivity {

  public static final int REQUEST_NEW_PIN = 27698;

  private static final String IS_NEW_PIN = "is_new_pin";

  private final DynamicTheme dynamicTheme = new DynamicRegistrationTheme();

  public static Intent getIntentForPinCreate(@NonNull Context context) {
    return getIntent(context, true);
  }

  public static Intent getIntentForPinUpdate(@NonNull Context context) {
    return getIntent(context, false);
  }

  private static Intent getIntent(@NonNull Context context, boolean isNewPin) {
    Intent intent = new Intent(context, CreateKbsPinActivity.class);
    intent.putExtra(IS_NEW_PIN, isNewPin);
    return intent;
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    if (KeyCachingService.isLocked(this)) {
      startActivity(getPromptPassphraseIntent());
      finish();
      return;
    }

    dynamicTheme.onCreate(this);

    setContentView(R.layout.create_kbs_pin_activity);

    CreateKbsPinFragmentArgs arguments = CreateKbsPinFragmentArgs.fromBundle(getIntent().getExtras());

    NavGraph graph = Navigation.findNavController(this, R.id.nav_host_fragment).getGraph();
    Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(graph, arguments.toBundle());
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private Intent getPromptPassphraseIntent() {
    return getRoutedIntent(PassphrasePromptActivity.class, getIntent());
  }

  private Intent getRoutedIntent(Class<?> destination, @Nullable Intent nextIntent) {
    final Intent intent = new Intent(this, destination);
    if (nextIntent != null)   intent.putExtra("next_intent", nextIntent);
    return intent;
  }
}
