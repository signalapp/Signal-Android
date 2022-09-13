package org.thoughtcrime.securesms.delete;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

public class DeleteAccountCountryPickerFragment extends DialogFragment {

  private DeleteAccountViewModel viewModel;

  public static void show(@NonNull FragmentManager fragmentManager) {
    new DeleteAccountCountryPickerFragment().show(fragmentManager, null);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.delete_account_country_picker, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar                           toolbar      = view.findViewById(R.id.delete_account_country_picker_toolbar);
    EditText                          searchFilter = view.findViewById(R.id.delete_account_country_picker_filter);
    RecyclerView                      recycler     = view.findViewById(R.id.delete_account_country_picker_recycler);
    DeleteAccountCountryPickerAdapter adapter      = new DeleteAccountCountryPickerAdapter(this::onCountryPicked);

    recycler.setAdapter(adapter);

    toolbar.setNavigationOnClickListener(unused -> dismiss());

    viewModel = new ViewModelProvider(requireActivity()).get(DeleteAccountViewModel.class);
    viewModel.getFilteredCountries().observe(getViewLifecycleOwner(), adapter::submitList);

    searchFilter.addTextChangedListener(new AfterTextChanged(this::onQueryChanged));
  }

  private void onQueryChanged(@NonNull Editable e) {
    viewModel.onQueryChanged(e.toString());
  }

  private void onCountryPicked(@NonNull Country country) {
    viewModel.onRegionSelected(country.getRegion());
    dismissAllowingStateLoss();
  }
}
