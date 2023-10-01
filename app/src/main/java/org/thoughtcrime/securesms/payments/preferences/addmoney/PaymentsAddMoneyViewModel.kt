package org.thoughtcrime.securesms.payments.preferences.addmoney

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.core.util.Result
import org.signal.core.util.StringUtil

internal class PaymentsAddMoneyViewModel(paymentsAddMoneyRepository: PaymentsAddMoneyRepository) : ViewModel() {
  private val selfAddressAndUri = MutableLiveData<AddressAndUri>()
  private val walletDisposable: Disposable

  val errors = MutableLiveData<PaymentsAddMoneyRepository.Error>()
  val selfAddressB58: LiveData<String> = selfAddressAndUri.map { it!!.addressB58 }
  val selfAddressAbbreviated: LiveData<CharSequence?> = selfAddressB58.map { StringUtil.abbreviateInMiddle(it, 17) }

  init {
    walletDisposable = paymentsAddMoneyRepository
      .getWalletAddress()
      .subscribe { result ->
        when (result) {
          is Result.Success -> selfAddressAndUri.postValue(result.success)
          is Result.Failure -> errors.postValue(result.failure)
        }
      }
  }

  override fun onCleared() {
    walletDisposable.dispose()
  }

  class Factory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PaymentsAddMoneyViewModel(PaymentsAddMoneyRepository()))!!
    }
  }
}
