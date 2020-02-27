package org.thoughtcrime.securesms.database.loaders;


import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class CountryListLoader extends AsyncTaskLoader<ArrayList<Map<String, String>>> {

  public CountryListLoader(Context context) {
    super(context);
  }

  @Override
  public ArrayList<Map<String, String>> loadInBackground() {
    return new ArrayList<>();
  }

  private static class RegionComparator implements Comparator<Map<String, String>> {
    @Override
    public int compare(Map<String, String> lhs, Map<String, String> rhs) {
      return lhs.get("country_name").compareTo(rhs.get("country_name"));
    }
  }
}
