package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper.DirectoryResult;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.SetUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class ContactDiscoveryV1 {

  private static final String TAG = ContactDiscoveryV1.class.getSimpleName();

  static @NonNull DirectoryResult getDirectoryResult(@NonNull Set<String> databaseNumbers,
                                                     @NonNull Set<String> systemNumbers)
      throws IOException
  {
    Set<String>                         allNumbers    = SetUtil.union(databaseNumbers, systemNumbers);
    FuzzyPhoneNumberHelper.InputResult  inputResult   = FuzzyPhoneNumberHelper.generateInput(allNumbers, databaseNumbers);
    List<ContactTokenDetails>           activeTokens  = getTokens(inputResult.getNumbers());
    Set<String>                         activeNumbers = Stream.of(activeTokens).map(ContactTokenDetails::getNumber).collect(Collectors.toSet());
    FuzzyPhoneNumberHelper.OutputResult outputResult  = FuzzyPhoneNumberHelper.generateOutput(activeNumbers, inputResult);
    HashMap<String, UUID>               uuids         = new HashMap<>();

    for (String number : outputResult.getNumbers()) {
      uuids.put(number, null);
    }

    return new DirectoryResult(uuids, outputResult.getRewrites());
  }

  static @NonNull DirectoryResult getDirectoryResult(@NonNull String number) throws  IOException {
    return getDirectoryResult(Collections.singleton(number), Collections.singleton(number));
  }

  private static @NonNull List<ContactTokenDetails> getTokens(@NonNull Set<String> numbers) throws IOException {
    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

    if (numbers.size() == 1) {
      Optional<ContactTokenDetails> details = accountManager.getContact(numbers.iterator().next());
      return details.isPresent() ? Collections.singletonList(details.get()) : Collections.emptyList();
    } else {
      return accountManager.getContacts(numbers);
    }
  }
}
