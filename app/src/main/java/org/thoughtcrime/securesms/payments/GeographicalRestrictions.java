package org.thoughtcrime.securesms.payments;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.MapUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.annotations.NonNull;

public final class GeographicalRestrictions {

  private static final String TAG = Log.tag(GeographicalRestrictions.class);

  private static final Validator BUILD_CONFIG_VALIDATOR = new BuildConfigWhitelistValidator();

  private GeographicalRestrictions() {
  }

  public static boolean e164Allowed(@Nullable String e164) {
    return selectValidator(FeatureFlags.mobileCoinBlacklist()).e164Allowed(e164);
  }

  @VisibleForTesting
  static boolean e164Allowed(@Nullable String e164, @Nullable String featureFlagBlacklist) {
    return selectValidator(featureFlagBlacklist).e164Allowed(e164);
  }

  public static @NonNull Validator selectValidator(@Nullable String featureFlagBlacklist) {
    String[] blacklist = parseBlacklist(featureFlagBlacklist);
    if (blacklist == null || blacklist.length == 0) {
      return BUILD_CONFIG_VALIDATOR;
    } else {
      return new FeatureFlagBlacklistValidator(blacklist);
    }
  }

  public static @Nullable String[] parseBlacklist(@Nullable String featureFlagBlacklist) {
    if (Util.isEmpty(featureFlagBlacklist)) {
      return null;
    }

    String[] parts = featureFlagBlacklist.split(",");
    if (parts.length == 0) {
      return null;
    }

    return parts;
  }

  private static class FeatureFlagBlacklistValidator implements Validator {

    private final Set<Integer>               blacklistCountries;
    private final Map<Integer, Set<Integer>> blacklistRegions;

    private FeatureFlagBlacklistValidator(@NonNull String[] blacklist) {
      Set<Integer>               countries = new HashSet<>(blacklist.length);
      Map<Integer, Set<Integer>> regions   = new HashMap<>();

      for (final String entry : blacklist) {
        try {
          String[] parts       = entry.trim().split(" ");
          Integer  countryCode = Integer.parseInt(parts[0].trim());

          if (parts.length == 1) {
            countries.add(countryCode);
          } else if (parts.length == 2) {
            Integer      regionCode        = Integer.parseInt(parts[1].trim());
            Set<Integer> regionsForCountry = MapUtil.getOrDefault(regions, countryCode, new HashSet<>());

            regionsForCountry.add(regionCode);
            regions.put(countryCode, regionsForCountry);
          } else {
            Log.w(TAG, "Bad entry: " + entry.trim());
          }
        } catch (NumberFormatException e) {
          Log.w(TAG, "Failure parsing part", e);
        }
      }

      for (final Integer countryCode : regions.keySet()) {
        Set<Integer> regionCodes = regions.get(countryCode);
        if (regionCodes != null) {
          regions.put(countryCode, Collections.unmodifiableSet(regionCodes));
        }
      }

      blacklistCountries = Collections.unmodifiableSet(countries);
      blacklistRegions   = Collections.unmodifiableMap(regions);
    }

    @Override
    public boolean e164Allowed(@Nullable String e164) {
      try {
        Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtil.getInstance().parse(e164, null);

        int countryCode = phoneNumber.getCountryCode();
        if (blacklistCountries.contains(countryCode)) {
          return false;
        }

        if (blacklistRegions.containsKey(countryCode)) {
          Set<Integer> regionsInCountry = blacklistRegions.get(countryCode);
          if (regionsInCountry == null) {
            return true;
          }

          int nationalDestinationCodeLength = PhoneNumberUtil.getInstance().getLengthOfNationalDestinationCode(phoneNumber);
          if (nationalDestinationCodeLength > 0) {
            String nationalSignificantNumber = PhoneNumberUtil.getInstance().getNationalSignificantNumber(phoneNumber);
            int    nationalDestinationCode   = Integer.parseInt(nationalSignificantNumber.substring(0, nationalDestinationCodeLength));

            if (regionsInCountry.contains(nationalDestinationCode)) {
              return false;
            }
          }

          int areaCodeLength = PhoneNumberUtil.getInstance().getLengthOfGeographicalAreaCode(phoneNumber);
          if (areaCodeLength > 0) {
            String nationalSignificantNumber = PhoneNumberUtil.getInstance().getNationalSignificantNumber(phoneNumber);
            int    areaCode                  = Integer.parseInt(nationalSignificantNumber.substring(0, areaCodeLength));

            if (regionsInCountry.contains(areaCode)) {
              return false;
            }
          }
        }

        return true;
      } catch (NumberParseException e) {
        Log.w(TAG, e);
        return false;
      }
    }
  }

  private static class BuildConfigWhitelistValidator implements Validator {
    private static final Set<Integer> REGION_CODE_SET;

    static {
      Set<Integer> set = new HashSet<>(BuildConfig.MOBILE_COIN_REGIONS.length);

      for (int i = 0; i < BuildConfig.MOBILE_COIN_REGIONS.length; i++) {
        set.add(BuildConfig.MOBILE_COIN_REGIONS[i]);
      }

      REGION_CODE_SET = Collections.unmodifiableSet(set);
    }

    private static boolean regionAllowed(int regionCode) {
      return REGION_CODE_SET.contains(regionCode);
    }

    @Override
    public boolean e164Allowed(@Nullable String e164) {
      try {
        int countryCode = PhoneNumberUtil.getInstance()
                                         .parse(e164, null)
                                         .getCountryCode();

        return regionAllowed(countryCode);
      } catch (NumberParseException e) {
        Log.w(TAG, e);
        return false;
      }
    }
  }

  private interface Validator {
    boolean e164Allowed(@Nullable String e164);
  }
}
