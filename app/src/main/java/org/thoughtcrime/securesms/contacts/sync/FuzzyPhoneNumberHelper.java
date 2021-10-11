package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A helper class to match a single number with multiple possible registered numbers. An example is
 * Mexican phone numbers, which recently removed a '1' after their country code. The idea is that
 * when doing contact intersection, we can try both with and without the '1' and make a decision
 * based on the results.
 */
class FuzzyPhoneNumberHelper {

  private final static List<FuzzyVariant> FUZZY_VARIANTS = Arrays.asList(new MxFuzzyVariant(), new ArFuzzyVariant());

  /**
   * This should be run on the list of eligible numbers for contact intersection so that we can
   * create an updated list that has potentially more "fuzzy" number matches in it.
   */
  static @NonNull InputResult generateInput(@NonNull Collection<String> testNumbers, @NonNull Collection<String> storedNumbers) {
    Set<String>         allNumbers = new HashSet<>(testNumbers);
    Map<String, String> fuzzies    = new HashMap<>();

    for (String number : testNumbers) {
      for (FuzzyVariant fuzzyVariant: FUZZY_VARIANTS) {
        if(fuzzyVariant.hasVariants(number)) {
          String variant = fuzzyVariant.getVariant(number);
          if(variant != null && !storedNumbers.contains(variant) && allNumbers.add(variant)) {
            fuzzies.put(number, variant);
          }
        }
      }
    }

    return new InputResult(allNumbers, fuzzies);
  }

  /**
   * This should be run on the list of numbers we find out are registered with the server. Based on
   * these results and our initial input set, we can decide if we need to rewrite which number we
   * have stored locally.
   */
  static @NonNull OutputResult generateOutput(@NonNull Map<String, UUID> registeredNumbers, @NonNull InputResult inputResult) {
    Map<String, UUID>   allNumbers = new HashMap<>(registeredNumbers);
    Map<String, String> rewrites   = new HashMap<>();

    for (Map.Entry<String, String> entry : inputResult.getFuzzies().entrySet()) {
      if (registeredNumbers.containsKey(entry.getKey()) && registeredNumbers.containsKey(entry.getValue())) {
        for (FuzzyVariant fuzzyVariant: FUZZY_VARIANTS) {
          if(fuzzyVariant.hasVariants(entry.getKey())) {
            if (fuzzyVariant.isDefaultVariant(entry.getKey())) {
              allNumbers.remove(entry.getValue());
            } else {
              rewrites.put(entry.getKey(), entry.getValue());
              allNumbers.remove(entry.getKey());
            }
          }
        }
      } else if (registeredNumbers.containsKey(entry.getValue())) {
        rewrites.put(entry.getKey(), entry.getValue());
        allNumbers.remove(entry.getKey());
      }
    }

    return new OutputResult(allNumbers, rewrites);
  }

  private interface FuzzyVariant {
    boolean hasVariants(@NonNull String number);
    String getVariant(@NonNull String number);
    boolean isDefaultVariant(@NonNull String number);
  }

  private static class MxFuzzyVariant implements FuzzyVariant {

    @Override
    public boolean hasVariants(@NonNull String number) {
      return number.startsWith("+52") && (number.length() == 13 || number.length() == 14);
    }

    @Override
    public String getVariant(String number) {
      if(number.startsWith("+521") && number.length() == 14) {
        return "+52" + number.substring("+521".length());
      }
      if(number.startsWith("+52") && !number.startsWith("+521") && number.length() == 13) {
        return "+521" + number.substring("+52".length());
      }

      return null;
    }

    @Override
    public boolean isDefaultVariant(@NonNull String number) {
      return number.startsWith("+52") && !number.startsWith("+521") && number.length() == 13;
    }

  }

  private static class ArFuzzyVariant implements FuzzyVariant {

    @Override
    public boolean hasVariants(@NonNull String number) {
      return number.startsWith("+54") && (number.length() == 13 || number.length() == 14);
    }

    @Override
    public String getVariant(String number) {
      if(number.startsWith("+549") && number.length() == 14) {
        return "+54" + number.substring("+549".length());
      }
      if(number.startsWith("+54") && !number.startsWith("+549") && number.length() == 13) {
        return "+549" + number.substring("+54".length());
      }

      return null;
    }

    @Override
    public boolean isDefaultVariant(@NonNull String number) {
      return number.startsWith("+54") && !number.startsWith("+549") && number.length() == 13;
    }

  }

  public static class InputResult {
    private final Set<String>         numbers;
    private final Map<String, String> fuzzies;

    @VisibleForTesting
    InputResult(@NonNull Set<String> numbers, @NonNull Map<String, String> fuzzies) {
      this.numbers = numbers;
      this.fuzzies = fuzzies;
    }

    public @NonNull Set<String> getNumbers() {
      return numbers;
    }

    public @NonNull Map<String, String> getFuzzies() {
      return fuzzies;
    }
  }

  public static class OutputResult {
    private final Map<String, UUID>   numbers;
    private final Map<String, String> rewrites;

    private OutputResult(@NonNull Map<String, UUID> numbers, @NonNull Map<String, String> rewrites) {
      this.numbers  = numbers;
      this.rewrites = rewrites;
    }

    public @NonNull Map<String, UUID> getNumbers() {
      return numbers;
    }

    public @NonNull Map<String, String> getRewrites() {
      return rewrites;
    }
  }
}
