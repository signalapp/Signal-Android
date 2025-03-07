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
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.util.UsernameUtil

class FindByViewModel(
  mode: FindByMode
) : ViewModel() {

  private val internalState = mutableStateOf(
    FindByState.startingState(self = Recipient.self(), mode = mode)
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

  fun onCountrySelected(country: Country) {
    internalState.value = state.value.copy(selectedCountry = country)
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

    return when (val result = UsernameRepository.fetchAciForUsername(usernameString = username)) {
      UsernameRepository.UsernameAciFetchResult.NetworkError -> FindByResult.NotFound()
      UsernameRepository.UsernameAciFetchResult.NotFound -> FindByResult.NotFound()
      is UsernameRepository.UsernameAciFetchResult.Success -> FindByResult.Success(Recipient.externalUsername(result.aci, username).id)
    }
  }

  @WorkerThread
  private fun performPhoneLookup(context: Context): FindByResult {
    val stateSnapshot = state.value
    val countryCode = stateSnapshot.selectedCountry.countryCode
    val nationalNumber = stateSnapshot.userEntry.removePrefix(countryCode.toString())

    val e164 = "+$countryCode$nationalNumber"

    return when (val result = RecipientRepository.lookupNewE164(e164)) {
      RecipientRepository.LookupResult.InvalidEntry -> FindByResult.InvalidEntry
      RecipientRepository.LookupResult.NetworkError -> FindByResult.NetworkError
      is RecipientRepository.LookupResult.NotFound -> FindByResult.NotFound(result.recipientId)
      is RecipientRepository.LookupResult.Success -> FindByResult.Success(result.recipientId)
    }
  }

  fun filterCountries(filterBy: String) {
    if (filterBy.isEmpty()) {
      internalState.value = state.value.copy(
        query = filterBy,
        filteredCountries = emptyList()
      )
    } else {
      internalState.value = state.value.copy(
        query = filterBy,
        filteredCountries = state.value.supportedCountries.filter { country: Country ->
          country.name.contains(filterBy, ignoreCase = true) ||
            country.countryCode.toString().contains(filterBy) ||
            (filterBy.equals("usa", ignoreCase = true) && country.name.equals("United States", ignoreCase = true))
        }
      )
    }
  }
}
