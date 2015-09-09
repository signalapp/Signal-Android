//package org.thoughtcrime.redphone.ui;
//
//import android.app.Activity;
//import android.os.Bundle;
//import android.support.v4.app.LoaderManager;
//import android.support.v4.content.Loader;
//import android.text.Editable;
//import android.text.TextWatcher;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.EditText;
//import android.widget.ListView;
//import android.widget.SimpleAdapter;
//
//import com.actionbarsherlock.app.SherlockListFragment;
//
//import org.thoughtcrime.redphone.R;
//import org.thoughtcrime.redphone.registration.CountryListLoader;
//
//import java.util.ArrayList;
//import java.util.Map;
//
//public class CountrySelectionFragment extends SherlockListFragment implements LoaderManager.LoaderCallbacks<ArrayList<Map<String, String>>> {
//
//  private EditText countryFilter;
//  private CountrySelectedListener listener;
//
//  @Override
//  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
//    return inflater.inflate(R.layout.country_selection_fragment, container, false);
//  }
//
//  @Override
//  public void onActivityCreated(Bundle bundle) {
//    super.onActivityCreated(bundle);
//    this.countryFilter = (EditText)getView().findViewById(R.id.country_search);
//    this.countryFilter.addTextChangedListener(new FilterWatcher());
//    getLoaderManager().initLoader(0, null, this).forceLoad();
//  }
//
//  @Override
//  public void onAttach(Activity activity) {
//    super.onAttach(activity);
//    this.listener = (CountrySelectedListener)activity;
//  }
//
//  @Override
//  public void onListItemClick(ListView listView, View view, int position, long id) {
//    Map<String, String> item = (Map<String, String>)this.getListAdapter().getItem(position);
//    if (this.listener != null) {
//      this.listener.countrySelected(item.get("country_name"),
//                                    Integer.parseInt(item.get("country_code").substring(1)));
//    }
//  }
//
//  @Override
//  public Loader<ArrayList<Map<String, String>>> onCreateLoader(int arg0, Bundle arg1) {
//    return new CountryListLoader(getActivity());
//  }
//
//  @Override
//  public void onLoadFinished(Loader<ArrayList<Map<String, String>>> loader,
//                             ArrayList<Map<String, String>> results)
//  {
//    String[] from = {"country_name", "country_code"};
//    int[] to      = {R.id.country_name, R.id.country_code};
//    this.setListAdapter(new SimpleAdapter(getActivity(), results, R.layout.country_list_item, from, to));
//
//    if (this.countryFilter != null && this.countryFilter.getText().length() != 0) {
//      ((SimpleAdapter)getListAdapter()).getFilter().filter(this.countryFilter.getText().toString());
//    }
//  }
//
//  @Override
//  public void onLoaderReset(Loader<ArrayList<Map<String, String>>> arg0) {
//    this.setListAdapter(null);
//  }
//
//  public interface CountrySelectedListener {
//    public void countrySelected(String countryName, int countryCode);
//  }
//
//  private class FilterWatcher implements TextWatcher {
//
//    @Override
//    public void afterTextChanged(Editable s) {
//      if (getListAdapter() != null) {
//        ((SimpleAdapter)getListAdapter()).getFilter().filter(s.toString());
//      }
//    }
//
//    @Override
//    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//    }
//
//    @Override
//    public void onTextChanged(CharSequence s, int start, int before, int count) {
//    }
//  }
//}
