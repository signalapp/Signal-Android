package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.StringUtil;

final class PaymentsAddMoneyViewModel extends ViewModel {

  private final MutableLiveData<AddressAndUri>                    selfAddressAndUri = new MutableLiveData<>();
  private final MutableLiveData<PaymentsAddMoneyRepository.Error> errors            = new MutableLiveData<>();
  private final LiveData<Uri>                                     selfAddressUri;
  private final LiveData<String>                                  selfAddressB58;
  private final LiveData<CharSequence>                            selfAddressAbbreviated;

  PaymentsAddMoneyViewModel(@NonNull PaymentsAddMoneyRepository paymentsAddMoneyRepository) {
    paymentsAddMoneyRepository.getWalletAddress(new AsynchronousCallback.MainThread<AddressAndUri, PaymentsAddMoneyRepository.Error>() {
      @Override
      public void onComplete(@Nullable AddressAndUri result) {
        selfAddressAndUri.setValue(result);
      }

      @Override
      public void onError(@Nullable PaymentsAddMoneyRepository.Error error) {
        errors.setValue(error);
      }
    });

    selfAddressB58         = Transformations.map(selfAddressAndUri, AddressAndUri::getAddressB58);
    selfAddressUri         = Transformations.map(selfAddressAndUri, AddressAndUri::getUri);
    selfAddressAbbreviated = Transformations.map(selfAddressB58, longAddress -> StringUtil.abbreviateInMiddle(longAddress, 17));
  }

  LiveData<String> getSelfAddressB58() {
    return selfAddressB58;
  }

  LiveData<CharSequence> getSelfAddressAbbreviated() {
    return selfAddressAbbreviated;
  }

  LiveData<PaymentsAddMoneyRepository.Error> getErrors() {
    return errors;
  }

  LiveData<Uri> getSelfAddressUriForQr() {
    return selfAddressUri;
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new PaymentsAddMoneyViewModel(new PaymentsAddMoneyRepository()));
    }
  }
}
