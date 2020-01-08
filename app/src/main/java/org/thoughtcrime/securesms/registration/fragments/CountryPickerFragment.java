package org.thoughtcrime.securesms.registration.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.loaders.CountryListLoader;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;

import java.util.ArrayList;
import java.util.Map;

public final class CountryPickerFragment extends ListFragment implements LoaderManager.LoaderCallbacks<ArrayList<Map<String, String>>> {

  private EditText              countryFilter;
  private RegistrationViewModel model;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.fragment_registration_country_picker, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    model = BaseRegistrationFragment.getRegistrationViewModel(requireActivity());

    countryFilter = view.findViewById(R.id.country_search);

    countryFilter.addTextChangedListener(new FilterWatcher());
    LoaderManager.getInstance(this).initLoader(0, null, this).forceLoad();
  }

  @Override
  public void onListItemClick(@NonNull ListView listView, @NonNull View view, int position, long id) {
    Map<String, String> item = (Map<String, String>) getListAdapter().getItem(position);

    int    countryCode = Integer.parseInt(item.get("country_code").replace("+", ""));
    String countryName = item.get("country_name");

    model.onCountrySelected(countryName, countryCode);

    Navigation.findNavController(view).navigate(CountryPickerFragmentDirections.actionCountrySelected());
  }

  @Override
  public @NonNull Loader<ArrayList<Map<String, String>>> onCreateLoader(int id, @Nullable Bundle args) {
   return new CountryListLoader(getActivity());
  }

  @Override
  public void onLoadFinished(@NonNull Loader<ArrayList<Map<String, String>>> loader,
                             @NonNull ArrayList<Map<String, String>> results)
  {
    String[] from = { "country_name", "country_code" };
    int[]    to   = { R.id.country_name, R.id.country_code };

    setListAdapter(new SimpleAdapter(getActivity(), results, R.layout.country_list_item, from, to));

    applyFilter(countryFilter.getText());
  }

  private void applyFilter(@NonNull CharSequence text) {
    SimpleAdapter listAdapter = (SimpleAdapter) getListAdapter();

    if (listAdapter != null) {
      listAdapter.getFilter().filter(text);
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<ArrayList<Map<String, String>>> loader) {
    setListAdapter(null);
  }

  private class FilterWatcher implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      applyFilter(s);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }
}
