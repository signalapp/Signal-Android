package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper.DirectoryResult;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.SetUtil;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ACI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Uses CDS to map E164's to UUIDs.
 */
class ContactDiscoveryV3 {

  private static final String TAG = Log.tag(ContactDiscoveryV3.class);

  private static final int MAX_NUMBERS = 20_500;

  @WorkerThread
  static DirectoryResult getDirectoryResult(@NonNull Set<String> databaseNumbers, @NonNull Set<String> systemNumbers) throws IOException {
    Set<String>                        allNumbers       = SetUtil.union(databaseNumbers, systemNumbers);
    FuzzyPhoneNumberHelper.InputResult inputResult      = FuzzyPhoneNumberHelper.generateInput(allNumbers, databaseNumbers);
    Set<String>                        sanitizedNumbers = sanitizeNumbers(inputResult.getNumbers());
    Set<String>                        ignoredNumbers   = new HashSet<>();

    if (sanitizedNumbers.size() > MAX_NUMBERS) {
      Set<String> randomlySelected = randomlySelect(sanitizedNumbers, MAX_NUMBERS);

      ignoredNumbers   = SetUtil.difference(sanitizedNumbers, randomlySelected);
      sanitizedNumbers = randomlySelected;
    }

    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

    try {
      Map<String, ACI>                    results      = accountManager.getRegisteredUsersWithCdsh(sanitizedNumbers, BuildConfig.CDSH_PUBLIC_KEY, BuildConfig.CDSH_CODE_HASH);
      FuzzyPhoneNumberHelper.OutputResult outputResult = FuzzyPhoneNumberHelper.generateOutput(results, inputResult);

      return new DirectoryResult(outputResult.getNumbers(), outputResult.getRewrites(), ignoredNumbers);
    } catch (IOException e) {
      Log.w(TAG, "Attestation error.", e);
      throw new IOException(e);
    }
  }

  private static Set<String> sanitizeNumbers(@NonNull Set<String> numbers) {
    return numbers.stream().filter(number -> {
      try {
        return number.startsWith("+") && number.length() > 1 && number.charAt(1) != '0' && Long.parseLong(number.substring(1)) > 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }).collect(Collectors.toSet());
  }

  private static @NonNull Set<String> randomlySelect(@NonNull Set<String> numbers, int max) {
    List<String> list = new ArrayList<>(numbers);
    Collections.shuffle(list);

    return new HashSet<>(list.subList(0, max));
  }
}
