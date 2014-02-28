package org.thoughtcrime.securesms.database.loaders;


import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;


import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

public class CountryListLoader extends AsyncTaskLoader<ArrayList<Map<String, String>>> {

  public CountryListLoader(Context context) {
    super(context);
  }

  @Override
  public ArrayList<Map<String, String>> loadInBackground() {
    Set<String> regions                    = PhoneNumberUtil.getInstance().getSupportedRegions();
    ArrayList<Map<String, String>> results = new ArrayList<Map<String, String>>(regions.size());

    for (String region : regions) {
      Map<String, String> data = new HashMap<String, String>(2);
      data.put("country_name", PhoneNumberFormatter.getRegionDisplayName(region));
      data.put("country_code", "+" + PhoneNumberUtil.getInstance().getCountryCodeForRegion(region));
      results.add(data);
    }

    String language = TextSecurePreferences.getLanguage(getContext());
    RegionComparator regionComparator;
    if (language == null) {
      regionComparator = new RegionComparator();
    } else {
      regionComparator = new RegionComparator(new Locale(language));
    }

    Collections.sort(results, regionComparator);

    return results;
  }

  private class RegionComparator implements Comparator<Map<String, String>> {

    private final Collator collator;

    public RegionComparator() {
      collator = Collator.getInstance();
    }

    public RegionComparator(Locale locale) {
      collator = Collator.getInstance(locale);
    }

    @Override
    public int compare(Map<String, String> lhs, Map<String, String> rhs) {
      return collator.compare(lhs.get("country_name"), rhs.get("country_name"));
    }
  }
}
