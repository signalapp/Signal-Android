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


public class TermsFragment extends Fragment {

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
    return inflater.inflate(R.layout.pigeon_fragment_registration_terms, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    TextView mTermsTv = view.findViewById(R.id.terms_tv);

    StringBuilder builder = new StringBuilder();
    builder.append(getString(R.string.terms_and_policy_1));
    builder.append(getString(R.string.terms_and_policy_2));
    builder.append(getString(R.string.terms_and_policy_3));
    builder.append(getString(R.string.terms_and_policy_4));
    builder.append(getString(R.string.terms_and_policy_5));
    builder.append(getString(R.string.terms_and_policy_6));
    builder.append(getString(R.string.terms_and_policy_7));
    builder.append(getString(R.string.terms_and_policy_8));
    builder.append(getString(R.string.terms_and_policy_9));
    builder.append(getString(R.string.terms_and_policy_10));
    builder.append(getString(R.string.terms_and_policy_11));
    builder.append(getString(R.string.terms_and_policy_12));
    builder.append(getString(R.string.terms_and_policy_13));
    builder.append(getString(R.string.terms_and_policy_14));
    mTermsTv.setText(builder);
  }
}
