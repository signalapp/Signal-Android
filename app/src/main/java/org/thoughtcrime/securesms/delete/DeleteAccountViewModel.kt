/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import com.google.i18n.phonenumbers.AsYouTypeFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.deleteaccount.DeleteAccountAction
import org.signal.appsettings.deleteaccount.DeleteAccountEvent
import org.signal.appsettings.deleteaccount.DeleteAccountState
import org.signal.appsettings.deleteaccount.DeleteAccountState.Companion.UNKNOWN_REGION
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives both screens that let a user delete their account. An account with a phone number has to have that number
 * keyed back in before anything is torn down; a numberless one has a box ticked instead.
 */
class DeleteAccountViewModel(
  private val repository: DeleteAccountRepository = DeleteAccountRepository()
) : EventDrivenViewModel<DeleteAccountEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(DeleteAccountViewModel::class)

    private const val MAX_COUNTRY_CODE_LENGTH = 3
  }

  private val phoneNumberUtil = PhoneNumberUtil.getInstance()

  private val _state = MutableStateFlow(
    DeleteAccountState(
      hasPhoneNumber = !repository.isPhoneNumberless(),
      username = repository.getUsername(),
      walletBalance = repository.getFormattedWalletBalance()
    )
  )
  private val _actions = Channel<DeleteAccountAction>(Channel.BUFFERED)

  val state: StateFlow<DeleteAccountState> = _state.asStateFlow()
  val actions: Flow<DeleteAccountAction> = _actions.receiveAsFlow()

  private var formatter: AsYouTypeFormatter? = null
  private var formatterRegion: String? = null

  override suspend fun processEvent(event: DeleteAccountEvent) {
    when (event) {
      DeleteAccountEvent.NavigateBackClicked -> {
        _actions.send(DeleteAccountAction.NavigateBack)
      }
      DeleteAccountEvent.CountryPickerClicked -> {
        _actions.send(DeleteAccountAction.NavigateToCountryPicker)
      }
      is DeleteAccountEvent.CountrySelected -> {
        applyRegionSelected(event.regionCode)
      }
      is DeleteAccountEvent.CountryCodeChanged -> {
        applyCountryCodeChanged(event.countryCode)
      }
      is DeleteAccountEvent.NationalNumberChanged -> {
        applyNationalNumberChanged(event.nationalNumber)
      }
      DeleteAccountEvent.DeleteAccountClicked -> {
        applyDeleteAccountClicked()
      }
      is DeleteAccountEvent.ConfirmationCheckedChanged -> {
        applyConfirmationCheckedChanged(event.checked)
      }
      DeleteAccountEvent.DeletionConfirmed -> {
        applyDeletionConfirmed()
      }
      DeleteAccountEvent.LaunchAppSettingsClicked -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(DeleteAccountAction.LaunchAppSettings)
      }
      DeleteAccountEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private fun applyRegionSelected(regionCode: String) {
    val countryCode = repository.getRegionCountryCode(regionCode)
    val countryDisplayName = repository.getRegionDisplayName(regionCode)
    val formattedNumber = formatNumber(_state.value.nationalNumber, regionCode)

    _state.update {
      it.copy(
        regionCode = regionCode,
        countryDisplayName = countryDisplayName,
        countryCode = if (countryCode > 0) countryCode.toString() else it.countryCode,
        formattedNumber = formattedNumber
      )
    }
  }

  private fun applyCountryCodeChanged(countryCode: String) {
    val sanitized = countryCode.filter { it.isDigit() }.take(MAX_COUNTRY_CODE_LENGTH)
    val code = sanitized.toIntOrNull() ?: 0
    val currentRegion = _state.value.regionCode
    val regionCode = if (phoneNumberUtil.getRegionCodesForCountryCode(code).contains(currentRegion)) {
      currentRegion
    } else {
      phoneNumberUtil.getRegionCodeForCountryCode(code)
    }

    val countryDisplayName = repository.getRegionDisplayName(regionCode)
    val formattedNumber = formatNumber(_state.value.nationalNumber, regionCode)

    _state.update {
      it.copy(
        countryCode = sanitized,
        regionCode = regionCode,
        countryDisplayName = countryDisplayName,
        formattedNumber = formattedNumber
      )
    }
  }

  private fun applyNationalNumberChanged(nationalNumber: String) {
    val digits = nationalNumber.filter { it.isDigit() }
    val regionCode = regionCodeForNumber(digits, _state.value.regionCode)

    val countryDisplayName = repository.getRegionDisplayName(regionCode)
    val formattedNumber = formatNumber(digits, regionCode)

    _state.update {
      it.copy(
        nationalNumber = digits,
        regionCode = regionCode,
        countryDisplayName = countryDisplayName,
        formattedNumber = formattedNumber
      )
    }
  }

  private suspend fun applyDeleteAccountClicked() {
    val state = _state.value

    if (!state.hasPhoneNumber) {
      _state.update { it.copy(dialog = Dialog.ConfirmNumberlessDeletion()) }
      return
    }

    val countryCode = state.countryCode.toIntOrNull() ?: 0

    if (countryCode == 0) {
      _actions.send(DeleteAccountAction.ShowNoCountryCode)
      return
    }

    val nationalNumber = state.nationalNumber.toLongOrNull()
    if (nationalNumber == null) {
      _actions.send(DeleteAccountAction.ShowNoNationalNumber)
      return
    }

    val dialog = if (repository.isNumberMatch(countryCode, nationalNumber)) Dialog.ConfirmDeletion else Dialog.NumberDoesNotMatch
    _state.update { it.copy(dialog = dialog) }
  }

  private fun applyConfirmationCheckedChanged(checked: Boolean) {
    _state.update {
      if (it.dialog is Dialog.ConfirmNumberlessDeletion) it.copy(dialog = Dialog.ConfirmNumberlessDeletion(checked)) else it
    }
  }

  private suspend fun applyDeletionConfirmed() {
    _state.update { it.copy(dialog = Dialog.DeletingAccount) }

    val result = repository.deleteAccount { progress ->
      val progressDialog = when (progress) {
        DeleteAccountRepository.Progress.CancelingSubscription -> Dialog.CancelingSubscription
        is DeleteAccountRepository.Progress.LeavingGroups -> Dialog.LeavingGroups(totalCount = progress.totalCount, leaveCount = progress.leaveCount)
        DeleteAccountRepository.Progress.DeletingAccount -> Dialog.DeletingAccount
      }

      _state.update { it.copy(dialog = progressDialog) }
    }

    val dialog = when (result) {
      DeleteAccountRepository.DeletionResult.Success -> Dialog.None
      DeleteAccountRepository.DeletionResult.CancelSubscriptionFailed,
      DeleteAccountRepository.DeletionResult.LeaveGroupsFailed,
      DeleteAccountRepository.DeletionResult.ServerDeletionFailed -> Dialog.DeletionFailed
      DeleteAccountRepository.DeletionResult.LocalDataDeletionFailed -> Dialog.LocalDataDeletionFailed
    }

    _state.update { it.copy(dialog = dialog) }
  }

  /**
   * The region [nationalNumber] actually belongs to, which matters when several of them share a calling code. Falls
   * back to [fallback] when the number doesn't say.
   */
  private fun regionCodeForNumber(nationalNumber: String, fallback: String): String {
    if (nationalNumber.isEmpty()) {
      return fallback
    }

    return try {
      phoneNumberUtil.getRegionCodeForNumber(phoneNumberUtil.parse(nationalNumber, fallback)) ?: fallback
    } catch (_: NumberParseException) {
      fallback
    }
  }

  /** [nationalNumber] as the user should see it in the number field, formatted for [regionCode]. */
  private fun formatNumber(nationalNumber: String, regionCode: String): String {
    if (regionCode != formatterRegion) {
      formatter = if (regionCode.isNotEmpty() && regionCode != UNKNOWN_REGION) phoneNumberUtil.getAsYouTypeFormatter(regionCode) else null
      formatterRegion = regionCode
    }

    val formatter = this.formatter ?: return nationalNumber

    formatter.clear()

    var formatted = ""
    for (digit in nationalNumber) {
      formatted = formatter.inputDigit(digit)
    }

    return formatted
  }
}
