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
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;

public class DeleteAccountViewModel extends ViewModel {

  private final DeleteAccountRepository    repository;
  private final List<Country>              allCountries;
  private final LiveData<List<Country>>    filteredCountries;
  private final LiveData<String>           regionCode;
  private final MutableLiveData<Integer>   countryCode;
  private final MutableLiveData<String>    countryDisplayName;
  private final MutableLiveData<Long>      nationalNumber;
  private final MutableLiveData<String>    query;
  private final SingleLiveEvent<EventType> events;
  private final LiveData<NumberViewState>  numberViewState;

  public DeleteAccountViewModel(@NonNull DeleteAccountRepository repository) {
    this.repository         = repository;
    this.allCountries       = repository.getAllCountries();
    this.countryCode        = new DefaultValueLiveData<>(NumberViewState.INITIAL.getCountryCode());
    this.nationalNumber     = new DefaultValueLiveData<>(NumberViewState.INITIAL.getNationalNumber());
    this.countryDisplayName = new DefaultValueLiveData<>(NumberViewState.INITIAL.getCountryDisplayName());
    this.query              = new DefaultValueLiveData<>("");
    this.regionCode         = Transformations.map(countryCode, this::mapCountryCodeToRegionCode);
    this.filteredCountries  = Transformations.map(query, q -> Stream.of(allCountries).filter(country -> isMatch(q, country)).toList());

    LiveData<NumberViewState> partialViewState = LiveDataUtil.combineLatest(countryCode,
                                                                            countryDisplayName,
                                                                            DeleteAccountViewModel::getPartialNumberViewState);

    this.numberViewState = LiveDataUtil.combineLatest(partialViewState, nationalNumber, DeleteAccountViewModel::getCompleteNumberViewState);
    this.events          = new SingleLiveEvent<>();
  }

  @NonNull LiveData<List<Country>> getFilteredCountries() {
    return filteredCountries;
  }

  @NonNull LiveData<String> getCountryDisplayName() {
    return Transformations.distinctUntilChanged(Transformations.map(numberViewState, NumberViewState::getCountryDisplayName));
  }

  @NonNull LiveData<String> getRegionCode() {
    return Transformations.distinctUntilChanged(regionCode);
  }

  @NonNull LiveData<Integer> getCountryCode() {
    return Transformations.distinctUntilChanged(Transformations.map(numberViewState, NumberViewState::getCountryCode));
  }

  @NonNull SingleLiveEvent<EventType> getEvents() {
    return events;
  }

  @Nullable Long getNationalNumber() {
    Long number = nationalNumber.getValue();
    if (number == null || number == NumberViewState.INITIAL.getNationalNumber()) {
      return null;
    } else {
      return number;
    }
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
    Integer countryCode    = this.countryCode.getValue();
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

  void onCountrySelected(@Nullable String countryDisplayName, int countryCode) {
    if (countryDisplayName != null) {
      this.countryDisplayName.setValue(countryDisplayName);
    }

    this.countryCode.setValue(countryCode);
  }

  void setNationalNumber(long nationalNumber) {
    this.nationalNumber.setValue(nationalNumber);
  }

  private @NonNull String mapCountryCodeToRegionCode(int countryCode) {
    return PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
  }

  private static @NonNull NumberViewState getPartialNumberViewState(int countryCode, @Nullable String countryDisplayName) {
    return new NumberViewState.Builder().countryCode(countryCode).selectedCountryDisplayName(countryDisplayName).build();
  }

  private static @NonNull NumberViewState getCompleteNumberViewState(@NonNull NumberViewState partial, long nationalNumber) {
    return partial.toBuilder().nationalNumber(nationalNumber).build();
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
