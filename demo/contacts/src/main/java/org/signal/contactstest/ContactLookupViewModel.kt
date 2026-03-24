package org.signal.contactstest

import android.accounts.Account
import android.app.Application
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.signal.contacts.SystemContactsRepository
import org.signal.contacts.SystemContactsRepository.ContactDetails
import org.signal.contacts.SystemContactsRepository.ContactIterator
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log

class ContactLookupViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private val TAG = Log.tag(ContactLookupViewModel::class.java)
  }

  private val _contacts: MutableLiveData<List<ContactDetails>> = MutableLiveData()

  val contacts: LiveData<List<ContactDetails>>
    get() = _contacts

  fun onLookup(lookup: String) {
    SignalExecutors.BOUNDED.execute {
      val account: Account? = SystemContactsRepository.getOrCreateSystemAccount(
        context = getApplication(),
        applicationId = BuildConfig.APPLICATION_ID,
        accountDisplayName = "Test"
      )

      val startTime: Long = System.currentTimeMillis()

      if (account != null) {
        val contactList: List<ContactDetails> = SystemContactsRepository.getContactDetailsByQueries(
          context = getApplication(),
          queries = listOf(lookup),
          e164Formatter = { number -> PhoneNumberUtils.formatNumberToE164(number, "US") ?: number }
        ).use { it.toList() }

        _contacts.postValue(contactList)
      } else {
        Log.w(TAG, "Failed to create an account!")
      }

      Log.d(TAG, "Took ${System.currentTimeMillis() - startTime} ms to fetch contacts.")
    }
  }

  private fun ContactIterator.toList(): List<ContactDetails> {
    val list: MutableList<ContactDetails> = mutableListOf()
    forEach { list += it }
    return list
  }
}
