/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(
  private val onLaunchRegistration: () -> Unit
) : ViewModel() {

  private val _state = MutableStateFlow(MainScreenState())
  val state: StateFlow<MainScreenState> = _state.asStateFlow()

  fun onEvent(event: MainScreenEvents) {
    viewModelScope.launch {
      when (event) {
        MainScreenEvents.LaunchRegistration -> onLaunchRegistration()
      }
    }
  }

  class Factory(private val onLaunchRegistration: () -> Unit) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainScreenViewModel(onLaunchRegistration) as T
    }
  }
}
