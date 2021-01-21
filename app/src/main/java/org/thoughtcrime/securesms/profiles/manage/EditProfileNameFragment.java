package org.thoughtcrime.securesms.profiles.manage;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.signal.core.util.EditTextUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

/**
 * Simple fragment to edit your profile name.
 */
public class EditProfileNameFragment extends Fragment {

  public static final int NAME_MAX_GLYPHS = 26;

  private EditText givenName;
  private EditText familyName;

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.edit_profile_name_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.givenName  = view.findViewById(R.id.edit_profile_name_given_name);
    this.familyName = view.findViewById(R.id.edit_profile_name_family_name);

    this.givenName.setText(Recipient.self().getProfileName().getGivenName());
    this.familyName.setText(Recipient.self().getProfileName().getFamilyName());

    view.<Toolbar>findViewById(R.id.toolbar)
        .setNavigationOnClickListener(v -> Navigation.findNavController(view)
                                                     .popBackStack());

    EditTextUtil.addGraphemeClusterLimitFilter(givenName, NAME_MAX_GLYPHS);
    EditTextUtil.addGraphemeClusterLimitFilter(familyName, NAME_MAX_GLYPHS);

    this.givenName.addTextChangedListener(new AfterTextChanged(EditProfileNameFragment::trimFieldToMaxByteLength));
    this.familyName.addTextChangedListener(new AfterTextChanged(EditProfileNameFragment::trimFieldToMaxByteLength));

    view.findViewById(R.id.edit_profile_name_save).setOnClickListener(this::onSaveClicked);
  }

  private void onSaveClicked(View view) {
    ProfileName profileName = ProfileName.fromParts(givenName.getText().toString(), familyName.getText().toString());

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      DatabaseFactory.getRecipientDatabase(requireContext()).setProfileName(Recipient.self().getId(), profileName);
      ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      return null;
    }, (nothing) -> {
      Navigation.findNavController(view).popBackStack();
    });
  }

  public static void trimFieldToMaxByteLength(Editable s) {
    int trimmedLength = StringUtil.trimToFit(s.toString(), ProfileName.MAX_PART_LENGTH).length();

    if (s.length() > trimmedLength) {
      s.delete(trimmedLength, s.length());
    }
  }
}
