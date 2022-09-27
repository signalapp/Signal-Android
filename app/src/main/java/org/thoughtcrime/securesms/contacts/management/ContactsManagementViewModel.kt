package org.thoughtcrime.securesms.contacts.management

import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.recipients.Recipient

class ContactsManagementViewModel(private val repository: ContactsManagementRepository) : ViewModel() {

  @CheckResult
  fun hideContact(recipient: Recipient): Completable {
    return repository.hideContact(recipient).observeOn(AndroidSchedulers.mainThread())
  }

  @CheckResult
  fun blockContact(recipient: Recipient): Completable {
    return repository.blockContact(recipient).observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val repository: ContactsManagementRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ContactsManagementViewModel(repository)) as T
    }
  }
}
