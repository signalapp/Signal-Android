package pigeon.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import static pigeon.extensions.KotilinExtensionsKt.focusOnLeft;


public class HomePageFragment extends Fragment {

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.pigeon_fragment_home_page, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);


    TextView newMessageButton = view.findViewById(R.id.new_message_button);
    focusOnLeft(newMessageButton);
    TextView newGroupButton = view.findViewById(R.id.new_group_button);
    focusOnLeft(newGroupButton);
    TextView markAllRead_button = view.findViewById(R.id.mark_all_read_button);
    focusOnLeft(markAllRead_button);
    TextView settingsButton = view.findViewById(R.id.settings_button);
    focusOnLeft(settingsButton);
    TextView searchButton = view.findViewById(R.id.search_button);
    focusOnLeft(searchButton);
  }
}
