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

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.util.List;

public class DeleteAccountViewModel extends ViewModel {

  private final DeleteAccountRepository    repository;
  private final List<Country>              allCountries;
  private final LiveData<List<Country>>    filteredCountries;
  private final MutableLiveData<String>    regionCode;
  private final LiveData<String>           countryDisplayName;
  private final MutableLiveData<Long>      nationalNumber;
  private final MutableLiveData<String>    query;
  private final SingleLiveEvent<EventType> events;

  public DeleteAccountViewModel(@NonNull DeleteAccountRepository repository) {
    this.repository         = repository;
    this.allCountries       = repository.getAllCountries();
    this.regionCode         = new DefaultValueLiveData<>(PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY);
    this.nationalNumber     = new MutableLiveData<>();
    this.query              = new DefaultValueLiveData<>("");
    this.countryDisplayName = Transformations.map(regionCode, repository::getRegionDisplayName);
    this.filteredCountries  = Transformations.map(query, q -> Stream.of(allCountries).filter(country -> isMatch(q, country)).toList());
    this.events             = new SingleLiveEvent<>();
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

  @NonNull SingleLiveEvent<EventType> getEvents() {
    return events;
  }

  @Nullable Long getNationalNumber() {
    return nationalNumber.getValue();
  }

  void onQueryChanged(@NonNull String query) {
    this.query.setValue(query.toLowerCase());
  }

  void deleteAccount() {
    repository.deleteAccount(() -> events.postValue(EventType.PIN_DELETION_FAILED),
                             () -> events.postValue(EventType.SERVER_DELETION_FAILED),
                             () -> events.postValue(EventType.LOCAL_DATA_DELETION_FAILED));
  }

  void submit() {
    String  region         = this.regionCode.getValue();
    Integer countryCode    = region != null ? repository.getRegionCountryCode(region) : null;
    Long    nationalNumber = this.nationalNumber.getValue();

    if (countryCode == null || countryCode == 0) {
      events.setValue(EventType.NO_COUNTRY_CODE);
      return;
    }

    if (nationalNumber == null) {
      events.setValue(EventType.NO_NATIONAL_NUMBER);
      return;
    }

    Phonenumber.PhoneNumber number = new Phonenumber.PhoneNumber();
    number.setCountryCode(countryCode);
    number.setNationalNumber(nationalNumber);

    if (PhoneNumberUtil.getInstance().isNumberMatch(number, Recipient.self().requireE164()) == PhoneNumberUtil.MatchType.EXACT_MATCH) {
      events.setValue(EventType.CONFIRM_DELETION);
    } else {
      events.setValue(EventType.NOT_A_MATCH);
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

  private static boolean isMatch(@NonNull String query, @NonNull Country country) {
    if (TextUtils.isEmpty(query)) {
      return true;
    } else {
      return country.getNormalizedDisplayName().contains(query.toLowerCase());
    }
  }

  enum EventType {
    NO_COUNTRY_CODE,
    NO_NATIONAL_NUMBER,
    NOT_A_MATCH,
    CONFIRM_DELETION,
    PIN_DELETION_FAILED,
    SERVER_DELETION_FAILED,
    LOCAL_DATA_DELETION_FAILED
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
