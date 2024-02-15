/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.signal.core.util.concurrent.safeBlockingGet
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.phonenumbers.NumberUtil
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.util.CountryPrefix
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter
import java.util.concurrent.TimeUnit

class FindByViewModel(
  mode: FindByMode
) : ViewModel() {

  private val internalState = mutableStateOf(
    FindByState(
      mode = mode
    )
  )

  val state: State<FindByState> = internalState

  fun onUserEntryChanged(userEntry: String) {
    val cleansed = if (state.value.mode == FindByMode.PHONE_NUMBER) {
      userEntry.filter { it.isDigit() }
    } else {
      userEntry
    }

    internalState.value = state.value.copy(userEntry = cleansed)
  }

  fun onCountryPrefixSearchEntryChanged(searchEntry: String) {
    internalState.value = state.value.copy(countryPrefixSearchEntry = searchEntry)
  }

  fun onCountryPrefixSelected(countryPrefix: CountryPrefix) {
    internalState.value = state.value.copy(selectedCountryPrefix = countryPrefix)
  }

  suspend fun onNextClicked(context: Context): FindByResult {
    internalState.value = state.value.copy(isLookupInProgress = true)
    val findByResult = viewModelScope.async(context = Dispatchers.IO) {
      if (state.value.mode == FindByMode.USERNAME) {
        performUsernameLookup()
      } else {
        performPhoneLookup(context)
      }
    }.await()

    internalState.value = state.value.copy(isLookupInProgress = false)
    return findByResult
  }

  @WorkerThread
  private fun performUsernameLookup(): FindByResult {
    val username = state.value.userEntry

    if (!UsernameUtil.isValidUsernameForSearch(username)) {
      return FindByResult.InvalidEntry
    }

    return when (val result = UsernameRepository.fetchAciForUsername(username = username).safeBlockingGet()) {
      UsernameRepository.UsernameAciFetchResult.NetworkError -> FindByResult.NotFound()
      UsernameRepository.UsernameAciFetchResult.NotFound -> FindByResult.NotFound()
      is UsernameRepository.UsernameAciFetchResult.Success -> FindByResult.Success(Recipient.externalUsername(result.aci, username).id)
    }
  }

  @WorkerThread
  private fun performPhoneLookup(context: Context): FindByResult {
    val stateSnapshot = state.value
    val countryCode = stateSnapshot.selectedCountryPrefix.digits
    val nationalNumber = stateSnapshot.userEntry.removePrefix(countryCode.toString())

    val e164 = "$countryCode$nationalNumber"

    if (!NumberUtil.isVisuallyValidNumber(e164)) {
      return FindByResult.InvalidEntry
    }

    val recipient = try {
      Recipient.external(context, e164)
    } catch (e: Exception) {
      return FindByResult.InvalidEntry
    }

    return if (!recipient.isRegistered || !recipient.hasServiceId()) {
      try {
        ContactDiscovery.refresh(context, recipient, false, TimeUnit.SECONDS.toMillis(10))
        val resolved = Recipient.resolved(recipient.id)
        if (!resolved.isRegistered) {
          if (PhoneNumberFormatter.isValidNumber(nationalNumber, countryCode.toString())) {
            FindByResult.NotFound(recipient.id)
          } else {
            FindByResult.InvalidEntry
          }
        } else {
          FindByResult.Success(recipient.id)
        }
      } catch (e: Exception) {
        if (PhoneNumberFormatter.isValidNumber(nationalNumber, countryCode.toString())) {
          FindByResult.NotFound(recipient.id)
        } else {
          FindByResult.InvalidEntry
        }
      }
    } else {
      FindByResult.Success(recipient.id)
    }
  }
}
