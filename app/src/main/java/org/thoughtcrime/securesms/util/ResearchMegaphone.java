package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses a comma-separated list of country codes colon-separated from how many buckets out of 1 million
 * should be enabled to see this megaphone in that country code. At the end of the list, an optional
 * element saying how many buckets out of a million should be enabled for all countries not listed previously
 * in the list. For example, "1:20000,*:40000" would mean 2% of the NANPA phone numbers and 4% of the rest of
 * the world should see the megaphone.
 */
public final class ResearchMegaphone {

  private static final String TAG = Log.tag(ResearchMegaphone.class);

  private static final String COUNTRY_WILDCARD = "*";

  /**
   * In research megaphone group for given country code
   */
  public static boolean isInResearchMegaphone() {
    Map<String, Integer> countryCountEnabled = parseCountryCounts(FeatureFlags.researchMegaphone());
    Recipient            self                = Recipient.self();

    if (countryCountEnabled.isEmpty() || !self.getE164().isPresent() || !self.getUuid().isPresent()) {
      return false;
    }

    long countEnabled      = determineCountEnabled(countryCountEnabled, self.getE164().or(""));
    long currentUserBucket = BucketingUtil.bucket(FeatureFlags.RESEARCH_MEGAPHONE_1, self.requireUuid(), 1_000_000);

    return countEnabled > currentUserBucket;
  }

  @VisibleForTesting
  static @NonNull Map<String, Integer> parseCountryCounts(@NonNull String buckets) {
    Map<String, Integer> countryCountEnabled = new HashMap<>();

    for (String bucket : buckets.split(",")) {
      String[] parts = bucket.split(":");
      if (parts.length == 2 && !parts[0].isEmpty()) {
        countryCountEnabled.put(parts[0], Util.parseInt(parts[1], 0));
      }
    }
    return countryCountEnabled;
  }

  @VisibleForTesting
  static long determineCountEnabled(@NonNull Map<String, Integer> countryCountEnabled, @NonNull String e164) {
    Integer countEnabled = countryCountEnabled.get(COUNTRY_WILDCARD);
    try {
      String countryCode = String.valueOf(PhoneNumberUtil.getInstance().parse(e164, "").getCountryCode());
      if (countryCountEnabled.containsKey(countryCode)) {
        countEnabled = countryCountEnabled.get(countryCode);
      }
    } catch (NumberParseException e) {
      Log.d(TAG, "Unable to determine country code for bucketing.");
      return 0;
    }

    return countEnabled != null ? countEnabled : 0;
  }
}
