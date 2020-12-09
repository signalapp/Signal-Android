package org.thoughtcrime.securesms.delete;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.pin.KbsEnclaves;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;

class DeleteAccountRepository {
  private static final String TAG = Log.tag(DeleteAccountRepository.class);

  @NonNull List<Country> getAllCountries() {
    return Stream.of(PhoneNumberUtil.getInstance().getSupportedRegions())
                 .map(DeleteAccountRepository::getCountryForRegion)
                 .sorted(new RegionComparator())
                 .toList();
  }

  @NonNull String getRegionDisplayName(@NonNull String region) {
    return PhoneNumberFormatter.getRegionDisplayName(region).or("");
  }

  int getRegionCountryCode(@NonNull String region) {
    return PhoneNumberUtil.getInstance().getCountryCodeForRegion(region);
  }

  void deleteAccount(@NonNull Runnable onFailureToRemovePin,
                     @NonNull Runnable onFailureToDeleteFromService,
                     @NonNull Runnable onFailureToDeleteLocalData)
  {
    SignalExecutors.BOUNDED.execute(() -> {
      Log.i(TAG, "deleteAccount: attempting to remove pin...");

      try {
        ApplicationDependencies.getKeyBackupService(KbsEnclaves.current()).newPinChangeSession().removePin();
      } catch (UnauthenticatedResponseException | IOException e) {
        Log.w(TAG, "deleteAccount: failed to remove PIN", e);
        onFailureToRemovePin.run();
        return;
      }

      Log.i(TAG, "deleteAccount: successfully removed pin.");
      Log.i(TAG, "deleteAccount: attempting to delete account from server...");

      try {
        ApplicationDependencies.getSignalServiceAccountManager().deleteAccount();
      } catch (IOException e) {
        Log.w(TAG, "deleteAccount: failed to delete account from signal service", e);
        onFailureToDeleteFromService.run();
        return;
      }

      Log.i(TAG, "deleteAccount: successfully removed account from server");
      Log.i(TAG, "deleteAccount: attempting to delete user data and close process...");

      if (!ServiceUtil.getActivityManager(ApplicationDependencies.getApplication()).clearApplicationUserData()) {
        Log.w(TAG, "deleteAccount: failed to delete user data");
        onFailureToDeleteLocalData.run();
      }
    });
  }

  private static @NonNull Country getCountryForRegion(@NonNull String region) {
    return new Country(PhoneNumberFormatter.getRegionDisplayName(region).or(""),
                       PhoneNumberUtil.getInstance().getCountryCodeForRegion(region),
                       region);
  }

  private static class RegionComparator implements Comparator<Country> {

    private final Collator collator;

    RegionComparator() {
      collator = Collator.getInstance();
      collator.setStrength(Collator.PRIMARY);
    }

    @Override
    public int compare(Country lhs, Country rhs) {
      return collator.compare(lhs.getDisplayName(), rhs.getDisplayName());
    }
  }
}
