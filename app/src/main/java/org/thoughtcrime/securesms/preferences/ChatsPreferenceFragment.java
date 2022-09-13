package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.permissions.Permissions;

import network.loki.messenger.R;

public class ChatsPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String TAG = ChatsPreferenceFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_chats);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

}
