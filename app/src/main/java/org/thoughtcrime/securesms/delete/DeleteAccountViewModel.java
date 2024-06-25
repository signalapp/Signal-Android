package org.thoughtcrime.securesms.delete;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.List;
import java.util.Optional;

public class DeleteAccountViewModel extends ViewModel {

  private final DeleteAccountRepository             repository;
  private final List<Country>                       allCountries;
  private final LiveData<List<Country>>             filteredCountries;
  private final MutableLiveData<String>             regionCode;
  private final LiveData<String>                    countryDisplayName;
  private final MutableLiveData<Long>               nationalNumber;
  private final MutableLiveData<String>             query;
  private final SingleLiveEvent<DeleteAccountEvent> events;
  private final LiveData<Optional<String>>          walletBalance;

  public DeleteAccountViewModel(@NonNull DeleteAccountRepository repository) {
    this.repository         = repository;
    this.allCountries       = repository.getAllCountries();
    this.regionCode         = new DefaultValueLiveData<>("ZZ"); // PhoneNumberUtil private static final String UNKNOWN_REGION = "ZZ";
    this.nationalNumber     = new MutableLiveData<>();
    this.query              = new DefaultValueLiveData<>("");
    this.countryDisplayName = Transformations.map(regionCode, repository::getRegionDisplayName);
    this.filteredCountries  = Transformations.map(query, q -> Stream.of(allCountries).filter(country -> isMatch(q, country)).toList());
    this.events             = new SingleLiveEvent<>();
    this.walletBalance      = Transformations.map(SignalStore.payments().liveMobileCoinBalance(),
                                                  DeleteAccountViewModel::getFormattedWalletBalance);
  }

  @NonNull LiveData<Optional<String>> getWalletBalance() {
    return walletBalance;
  }

  @NonNull LiveData<List<Country>> getFilteredCountries() {
    return filteredCountries;
  }

  @NonNull LiveData<String> getCountryDisplayName() {
    return Transformations.distinctUntilChanged(countryDisplayName);
  }

  @NonNull LiveData<String> getRegionCode() {
    return Transformations.distinctUntilChanged(regionCode);
  }

  @NonNull SingleLiveEvent<DeleteAccountEvent> getEvents() {
    return events;
  }

  @Nullable Long getNationalNumber() {
    return nationalNumber.getValue();
  }

  void onQueryChanged(@NonNull String query) {
    this.query.setValue(query.toLowerCase());
  }

  void deleteAccount() {
    repository.deleteAccount(events::postValue);
  }

  void submit() {
    String  region         = this.regionCode.getValue();
    Integer countryCode    = region != null ? repository.getRegionCountryCode(region) : null;
    Long    nationalNumber = this.nationalNumber.getValue();

    if (countryCode == null || countryCode == 0) {
      events.setValue(DeleteAccountEvent.NoCountryCode.INSTANCE);
      return;
    }

    if (nationalNumber == null) {
      events.setValue(DeleteAccountEvent.NoNationalNumber.INSTANCE);
      return;
    }

    Phonenumber.PhoneNumber number = new Phonenumber.PhoneNumber();
    number.setCountryCode(countryCode);
    number.setNationalNumber(nationalNumber);

    final PhoneNumberUtil.MatchType matchType = PhoneNumberUtil.getInstance().isNumberMatch(number, Recipient.self().requireE164());
    if (matchType == PhoneNumberUtil.MatchType.EXACT_MATCH || matchType == PhoneNumberUtil.MatchType.SHORT_NSN_MATCH || matchType == PhoneNumberUtil.MatchType.NSN_MATCH) {
      events.setValue(DeleteAccountEvent.ConfirmDeletion.INSTANCE);
    } else {
      events.setValue(DeleteAccountEvent.NotAMatch.INSTANCE);
    }
  }

  void onCountrySelected(int countryCode) {
    String       region  = this.regionCode.getValue();
    List<String> regions = PhoneNumberUtil.getInstance().getRegionCodesForCountryCode(countryCode);

    if (!regions.contains(region)) {
      this.regionCode.setValue(PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode));
    }
  }

  void onRegionSelected(@NonNull String region) {
    this.regionCode.setValue(region);
  }

  void setNationalNumber(long nationalNumber) {
    this.nationalNumber.setValue(nationalNumber);

    try {
      String phoneNumberRegion = PhoneNumberUtil.getInstance()
                                                .getRegionCodeForNumber(PhoneNumberUtil.getInstance().parse(String.valueOf(nationalNumber),
                                                                        regionCode.getValue()));
      if (phoneNumberRegion != null) {
        regionCode.setValue(phoneNumberRegion);
      }
    } catch (NumberParseException ignored) {
    }
  }

  private static @NonNull Optional<String> getFormattedWalletBalance(@NonNull Balance balance) {
    Money amount = balance.getFullAmount();
    if (amount.isPositive()) {
      return Optional.of(amount.toString(FormatterOptions.defaults()));
    } else {
      return Optional.empty();
    }
  }

  private static boolean isMatch(@NonNull String query, @NonNull Country country) {
    if (TextUtils.isEmpty(query)) {
      return true;
    } else {
      return country.getNormalizedDisplayName().contains(query.toLowerCase());
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {

    private final DeleteAccountRepository repository;

    public Factory(DeleteAccountRepository repository) {
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new DeleteAccountViewModel(repository));
    }
  }
}
