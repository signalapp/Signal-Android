package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to match a single number with multiple possible registered numbers. An example is
 * Mexican phone numbers, which recently removed a '1' after their country code. The idea is that
 * when doing contact intersection, we can try both with and without the '1' and make a decision
 * based on the results.
 */
class FuzzyPhoneNumberHelper {

  private final static List<FuzzyMatcher> FUZZY_MATCHERS = Arrays.asList(new MexicoFuzzyMatcher(), new ArgentinaFuzzyMatcher());

  /**
   * This should be run on the list of eligible numbers for contact intersection so that we can
   * create an updated list that has potentially more "fuzzy" number matches in it.
   */
  static @NonNull InputResult generateInput(@NonNull Collection<String> testNumbers, @NonNull Collection<String> storedNumbers) {
    Set<String>         allNumbers        = new HashSet<>(testNumbers);
    Map<String, String> originalToVariant = new HashMap<>();

    for (String number : testNumbers) {
      for (FuzzyMatcher matcher : FUZZY_MATCHERS) {
        if(matcher.matches(number)) {
          String variant = matcher.getVariant(number);
          if(variant != null && !storedNumbers.contains(variant) && allNumbers.add(variant)) {
            originalToVariant.put(number, variant);
          }
        }
      }
    }

    return new InputResult(allNumbers, originalToVariant);
  }

  /**
   * This should be run on the list of numbers we find out are registered with the server. Based on
   * these results and our initial input set, we can decide if we need to rewrite which number we
   * have stored locally.
   */
  static @NonNull <E> OutputResult<E> generateOutput(@NonNull Map<String, E> registeredNumbers, @NonNull InputResult inputResult) {
    Map<String, E>      allNumbers = new HashMap<>(registeredNumbers);
    Map<String, String> rewrites   = new HashMap<>();

    for (Map.Entry<String, String> entry : inputResult.getMapOfOriginalToVariant().entrySet()) {
      String original = entry.getKey();
      String variant  = entry.getValue();

      if (registeredNumbers.containsKey(original) && registeredNumbers.containsKey(variant)) {
        for (FuzzyMatcher matcher: FUZZY_MATCHERS) {
          if(matcher.matches(original)) {
            if (matcher.isPreferredVariant(original)) {
              allNumbers.remove(variant);
            } else {
              rewrites.put(original, variant);
              allNumbers.remove(original);
            }
          }
        }
      } else if (registeredNumbers.containsKey(variant)) {
        rewrites.put(original, variant);
        allNumbers.remove(original);
      }
    }

    return new OutputResult<>(allNumbers, rewrites);
  }

  private interface FuzzyMatcher {
    boolean matches(@NonNull String number);
    @Nullable String getVariant(@NonNull String number);
    boolean isPreferredVariant(@NonNull String number);
  }

  /**
   * Mexico has an optional 1 after their +52 country code, e.g. both of these numbers are valid and map to the same logical number:
   * +525512345678
   * +5215512345678
   *
   * Mexico used to require the 1, but has since removed the requirement.
   */
  @VisibleForTesting
  static class MexicoFuzzyMatcher implements FuzzyMatcher {

    @Override
    public boolean matches(@NonNull String number) {
      return number.startsWith("+52") && (number.length() == 13 || number.length() == 14);
    }

    @Override
    public @Nullable String getVariant(String number) {
      if(number.startsWith("+521") && number.length() == 14) {
        return "+52" + number.substring("+521".length());
      }

      if(number.startsWith("+52") && !number.startsWith("+521") && number.length() == 13) {
        return "+521" + number.substring("+52".length());
      }

      return null;
    }

    @Override
    public boolean isPreferredVariant(@NonNull String number) {
      return number.startsWith("+52") && !number.startsWith("+521") && number.length() == 13;
    }
  }

  /**
   * Argentina has an optional 9 after their +54 country code, e.g. both of these numbers are valid and map to the same logical number:
   * +545512345678
   * +5495512345678
   */
  @VisibleForTesting
  static class ArgentinaFuzzyMatcher implements FuzzyMatcher {

    @Override
    public boolean matches(@NonNull String number) {
      return number.startsWith("+54") && (number.length() == 13 || number.length() == 14);
    }

    @Override
    public @Nullable String getVariant(String number) {
      if(number.startsWith("+549") && number.length() == 14) {
        return "+54" + number.substring("+549".length());
      }

      if(number.startsWith("+54") && !number.startsWith("+549") && number.length() == 13) {
        return "+549" + number.substring("+54".length());
      }

      return null;
    }

    @Override
    public boolean isPreferredVariant(@NonNull String number) {
      return number.startsWith("+549") && number.length() == 14;
    }
  }

  public static class InputResult {
    private final Set<String>         numbers;
    private final Map<String, String> originalToVariant;

    @VisibleForTesting
    InputResult(@NonNull Set<String> numbers, @NonNull Map<String, String> originalToVariant) {
      this.numbers           = numbers;
      this.originalToVariant = originalToVariant;
    }

    public @NonNull Set<String> getNumbers() {
      return numbers;
    }

    public @NonNull Map<String, String> getMapOfOriginalToVariant() {
      return originalToVariant;
    }
  }

  public static class OutputResult<E> {
    private final Map<String, E>   numbers;
    private final Map<String, String> rewrites;

    private OutputResult(@NonNull Map<String, E> numbers, @NonNull Map<String, String> rewrites) {
      this.numbers  = numbers;
      this.rewrites = rewrites;
    }

    public @NonNull Map<String, E> getNumbers() {
      return numbers;
    }

    public @NonNull Map<String, String> getRewrites() {
      return rewrites;
    }
  }
}
