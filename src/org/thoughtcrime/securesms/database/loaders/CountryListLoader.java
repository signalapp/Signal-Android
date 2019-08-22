package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;

import androidx.loader.content.AsyncTaskLoader;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CountryListLoader extends AsyncTaskLoader<ArrayList<Map<String, String>>> {

  public CountryListLoader(Context context) {
    super(context);
  }

  @Override
  public ArrayList<Map<String, String>> loadInBackground() {
    Set<String> regions                    = PhoneNumberUtil.getInstance().getSupportedRegions();
    ArrayList<Map<String, String>> results = new ArrayList<>(regions.size());

    for (String region : regions) {
      Map<String, String> data = new HashMap<>(2);
      data.put("country_name", PhoneNumberFormatter.getRegionDisplayName(region));
      data.put("country_code", "+" +PhoneNumberUtil.getInstance().getCountryCodeForRegion(region));
      results.add(data);
    }

    Collections.sort(results, new RegionComparator());

    return results;
  }

  private static class RegionComparator implements Comparator<Map<String, String>> {

    private final Collator collator;

    RegionComparator() {
      collator = Collator.getInstance();
      collator.setStrength(Collator.PRIMARY);
    }

    @Override
    public int compare(Map<String, String> lhs, Map<String, String> rhs) {
      String a = lhs.get("country_name");
      String b = rhs.get("country_name");
      return collator.compare(a, b);
    }
  }
}
