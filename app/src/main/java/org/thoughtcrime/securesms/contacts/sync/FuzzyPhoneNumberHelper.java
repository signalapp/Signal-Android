package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to match a single number with multiple possible registered numbers. An example is
 * Mexican phone numbers, which recently removed a '1' after their country code. The idea is that
 * when doing contact intersection, we can try both with and without the '1' and make a decision
 * based on the results.
 */
class FuzzyPhoneNumberHelper {

  /**
   * This should be run on the list of eligible numbers for contact intersection so that we can
   * create an updated list that has potentially more "fuzzy" number matches in it.
   */
  static @NonNull InputResult generateInput(@NonNull Collection<String> testNumbers, @NonNull Collection<String> storedNumbers) {
    Set<String>         allNumbers = new HashSet<>(testNumbers);
    Map<String, String> fuzzies    = new HashMap<>();

    for (String number : testNumbers) {
      if (mx(number)) {
        String add1   = mxAdd1(number);
        String strip1 = mxStrip1(number);

        if (mxMissing1(number) && !storedNumbers.contains(add1) && allNumbers.add(add1)) {
          fuzzies.put(number, add1);
        } else if (mxHas1(number) && !storedNumbers.contains(strip1) && allNumbers.add(strip1)) {
          fuzzies.put(number, strip1);
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
  static @NonNull OutputResult generateOutput(@NonNull Collection<String> registeredNumbers, @NonNull InputResult inputResult) {
    Set<String>         allNumbers = new HashSet<>(registeredNumbers);
    Map<String, String> rewrites   = new HashMap<>();

    for (Map.Entry<String, String> entry : inputResult.getFuzzies().entrySet()) {
      if (registeredNumbers.contains(entry.getKey()) && registeredNumbers.contains(entry.getValue())) {
        if (mxHas1(entry.getKey())) {
          rewrites.put(entry.getKey(), entry.getValue());
          allNumbers.remove(entry.getKey());
        } else {
          allNumbers.remove(entry.getValue());
        }
      } else if (registeredNumbers.contains(entry.getValue())) {
        rewrites.put(entry.getKey(), entry.getValue());
        allNumbers.remove(entry.getKey());
      }
    }

    return new OutputResult(allNumbers, rewrites);
  }


  private static boolean mx(@NonNull String number) {
    return number.startsWith("+52") && (number.length() == 13 || number.length() == 14);
  }

  private static boolean mxHas1(@NonNull String number) {
    return number.startsWith("+521") && number.length() == 14;
  }

  private static boolean mxMissing1(@NonNull String number) {
    return number.startsWith("+52") && !number.startsWith("+521") && number.length() == 13;
  }

  private static @NonNull String mxStrip1(@NonNull String number) {
    return mxHas1(number) ? "+52" + number.substring("+521".length())
                          : number;
  }

  private static @NonNull String mxAdd1(@NonNull String number) {
    return mxMissing1(number) ? "+521" + number.substring("+52".length())
                              : number;
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
    private final Set<String>         numbers;
    private final Map<String, String> rewrites;

    private OutputResult(@NonNull Set<String> numbers, @NonNull Map<String, String> rewrites) {
      this.numbers  = numbers;
      this.rewrites = rewrites;
    }

    public @NonNull Set<String> getNumbers() {
      return numbers;
    }

    public @NonNull Map<String, String> getRewrites() {
      return rewrites;
    }
  }
}
