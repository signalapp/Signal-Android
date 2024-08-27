package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.mms.PushMediaConstraints;
import org.thoughtcrime.securesms.notifications.DeviceSpecificNotificationConfig;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provide access to locale-specific values within remote config, following the locale CSV-Colon format.
 *
 * Example: countryCode:integerValue,countryCode:integerValue,*:integerValue
 */
public final class LocaleRemoteConfig {

  private static final String TAG = Log.tag(LocaleRemoteConfig.class);

  private static final String COUNTRY_WILDCARD = "*";
  private static final int    NOT_FOUND        = -1;

  public static @NonNull Optional<PushMediaConstraints.MediaConfig> getMediaQualityLevel() {
    Map<String, Integer> countryValues = parseCountryValues(RemoteConfig.getMediaQualityLevels(), NOT_FOUND);
    int                  level         = getCountryValue(countryValues, Recipient.self().getE164().orElse(""), NOT_FOUND);

    return Optional.ofNullable(PushMediaConstraints.MediaConfig.forLevel(level));
  }

  public static boolean shouldShowReleaseNote(@NonNull String releaseNoteUuid, @NonNull String countries) {
    return isEnabledPartsPerMillion(releaseNoteUuid, countries);
  }

  /**
   * @return Whether Google Pay is disabled in this region
   */
  public static boolean isGooglePayDisabled() {
    return isEnabledE164Start(RemoteConfig.googlePayDisabledRegions());
  }

  /**
   * @return Whether credit cards are disabled in this region
   */
  public static boolean isCreditCardDisabled() {
    return isEnabledE164Start(RemoteConfig.creditCardDisabledRegions());
  }

  /**
   * @return Whether PayPal is disabled in this region
   */
  public static boolean isPayPalDisabled() {
    return isEnabledE164Start(RemoteConfig.paypalDisabledRegions());
  }

  public static boolean isIdealEnabled() {
    return isEnabledE164Start(RemoteConfig.idealEnabledRegions());
  }

  public static boolean isSepaEnabled() {
    return isEnabledE164Start(RemoteConfig.sepaEnabledRegions());
  }

  public static boolean isDelayedNotificationPromptEnabled() {
    return RemoteConfig.internalUser() || isEnabledPartsPerMillion(RemoteConfig.PROMPT_FOR_NOTIFICATION_LOGS, RemoteConfig.promptForDelayedNotificationLogs());
  }

  public static boolean isBatterySaverPromptEnabled() {
    return RemoteConfig.internalUser() || isEnabledPartsPerMillion(RemoteConfig.PROMPT_BATTERY_SAVER, RemoteConfig.promptBatterySaver());
  }

  public static boolean isDeviceSpecificNotificationEnabled() {
    return isEnabledPartsPerMillion(RemoteConfig.DEVICE_SPECIFIC_NOTIFICATION_CONFIG, DeviceSpecificNotificationConfig.getCurrentConfig().getLocalePercent());
  }

  /**
   * Parses a comma-separated list of country codes and area codes to check if self's e164 starts with
   * one of them. For example, "33,1555" will return turn for e164's that start with 33 or look like 1-555-xxx-xxx.
   */
  private static boolean isEnabledE164Start(@NonNull String serialized) {
    Recipient self = Recipient.self();

    if (self.getE164().isEmpty()) {
      return false;
    }

    return isEnabledE164Start(serialized, self.getE164().get());
  }

  @VisibleForTesting
  static boolean isEnabledE164Start(@NonNull String serialized, @NonNull String e164) {
    List<String> countryAndAreaCodes = Arrays.stream(serialized.split(",")).map(s -> s.trim().replaceAll("\\s", "")).collect(Collectors.toList());
    String       e164Numbers         = e164.replaceAll("\\D", "");

    return countryAndAreaCodes.stream().anyMatch(e164Numbers::startsWith);
  }

  /**
   * Parses a comma-separated list of country codes colon-separated from how many buckets out of 1 million
   * should be enabled to see this megaphone in that country code. At the end of the list, an optional
   * element saying how many buckets out of a million should be enabled for all countries not listed previously
   * in the list. For example, "1:20000,*:40000" would mean 2% of the NANPA phone numbers and 4% of the rest of
   * the world should see the megaphone.
   */
  private static boolean isEnabledPartsPerMillion(@NonNull String flag, @NonNull String serialized) {
    Map<String, Integer> countryCodeValues = parseCountryValues(serialized, 0);
    Recipient            self              = Recipient.self();

    if (countryCodeValues.isEmpty() || !self.getE164().isPresent() || !self.getServiceId().isPresent()) {
      return false;
    }

    long countEnabled      = getCountryValue(countryCodeValues, self.getE164().orElse(""), 0);
    long currentUserBucket = BucketingUtil.bucket(flag, self.requireAci().getRawUuid(), 1_000_000);

    return countEnabled > currentUserBucket;
  }

  @VisibleForTesting
  static @NonNull Map<String, Integer> parseCountryValues(@NonNull String buckets, int defaultValue) {
    Map<String, Integer> countryCountEnabled = new HashMap<>();

    for (String bucket : buckets.split(",")) {
      String[] parts = bucket.split(":");
      if (parts.length == 2 && !parts[0].isEmpty()) {
        countryCountEnabled.put(parts[0], Util.parseInt(parts[1], defaultValue));
      }
    }
    return countryCountEnabled;
  }

  @VisibleForTesting
  static int getCountryValue(@NonNull Map<String, Integer> countryCodeValues, @NonNull String e164, int defaultValue) {
    Integer countEnabled = countryCodeValues.get(COUNTRY_WILDCARD);
    try {
      String countryCode = String.valueOf(PhoneNumberUtil.getInstance().parse(e164, "").getCountryCode());
      if (countryCodeValues.containsKey(countryCode)) {
        countEnabled = countryCodeValues.get(countryCode);
      }
    } catch (NumberParseException e) {
      Log.d(TAG, "Unable to determine country code for bucketing.");
      return defaultValue;
    }

    return countEnabled != null ? countEnabled : defaultValue;
  }
}
