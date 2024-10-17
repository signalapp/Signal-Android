package org.thoughtcrime.securesms.profiles.manage;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.EditTextUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

/**
 * Simple fragment to edit your profile name.
 */
public class EditProfileNameFragment extends Fragment {

  public static final int NAME_MAX_GLYPHS = 26;

  private EditText                       givenName;
  private EditText                       familyName;
  private CircularProgressMaterialButton saveButton;
  private EditProfileNameViewModel       viewModel;

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.edit_profile_name_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.givenName  = view.findViewById(R.id.edit_profile_name_given_name);
    this.familyName = view.findViewById(R.id.edit_profile_name_family_name);
    this.saveButton = view.findViewById(R.id.edit_profile_name_save);

    initializeViewModel();

    this.givenName.setText(Recipient.self().getProfileName().getGivenName());
    this.familyName.setText(Recipient.self().getProfileName().getFamilyName());

    viewModel.onGivenNameChanged(this.givenName.getText().toString());

    view.<Toolbar>findViewById(R.id.toolbar)
        .setNavigationOnClickListener(v -> Navigation.findNavController(view)
                                                     .popBackStack());

    EditTextUtil.addGraphemeClusterLimitFilter(givenName, NAME_MAX_GLYPHS);
    EditTextUtil.addGraphemeClusterLimitFilter(familyName, NAME_MAX_GLYPHS);

    this.givenName.addTextChangedListener(new AfterTextChanged(s -> {
      trimFieldToMaxByteLength(s);
      viewModel.onGivenNameChanged(s.toString());
    }));
    this.familyName.addTextChangedListener(new AfterTextChanged(EditProfileNameFragment::trimFieldToMaxByteLength));

    saveButton.setOnClickListener(v -> viewModel.onSaveClicked(requireContext(),
                                                               givenName.getText().toString(),
                                                               familyName.getText().toString()));

    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(this.givenName);
  }

  private void initializeViewModel() {
    this.viewModel = new ViewModelProvider(this).get(EditProfileNameViewModel.class);

    viewModel.getSaveState().observe(getViewLifecycleOwner(), this::presentSaveState);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::presentEvent);
  }

  private void presentSaveState(@NonNull EditProfileNameViewModel.SaveState state) {
    switch (state) {
      case DISABLED:
        saveButton.setClickable(false);
        saveButton.setAlpha(0.5f);
        setEditTextEnabled(givenName, true);
        setEditTextEnabled(familyName, true);
        break;
      case IDLE:
        saveButton.setClickable(true);
        saveButton.cancelSpinning();
        saveButton.setAlpha(1);
        setEditTextEnabled(givenName, true);
        setEditTextEnabled(familyName, true);
        break;
      case IN_PROGRESS:
        saveButton.setClickable(false);
        saveButton.setSpinning();
        saveButton.setAlpha(1);
        setEditTextEnabled(givenName, false);
        setEditTextEnabled(familyName, false);
        break;
      case DONE:
        saveButton.setClickable(false);
        Navigation.findNavController(requireView()).popBackStack();
        saveButton.setAlpha(1);
        setEditTextEnabled(givenName, false);
        setEditTextEnabled(familyName, false);
        break;
    }
  }

  private void presentEvent(@NonNull EditProfileNameViewModel.Event event) {
    if (event == EditProfileNameViewModel.Event.NETWORK_FAILURE) {
      Toast.makeText(requireContext(), R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later, Toast.LENGTH_SHORT).show();
    }
  }

  public static void trimFieldToMaxByteLength(Editable s) {
    trimFieldToMaxByteLength(s, ProfileName.MAX_PART_LENGTH);
  }

  public static void trimFieldToMaxByteLength(Editable s, int length) {
    int trimmedLength = StringUtil.trimToFit(s.toString(), length).length();

    if (s.length() > trimmedLength) {
      s.delete(trimmedLength, s.length());
    }
  }

  private static void setEditTextEnabled(@NonNull EditText text, boolean enabled) {
    text.setEnabled(enabled);
    text.setFocusable(enabled);
    if (enabled) {
      text.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    } else {
      text.clearFocus();
      text.setInputType(EditorInfo.TYPE_NULL);
    }
  }
}
