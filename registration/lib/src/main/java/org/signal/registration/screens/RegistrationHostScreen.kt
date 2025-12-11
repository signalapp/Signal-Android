/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import org.signal.registration.RegistrationNavHost
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationViewModel

/**
 * Entry point for the registration flow.
 *
 * This composable sets up the entire registration navigation flow and can be
 * embedded into the main app's navigation or launched as a standalone flow.
 *
 * @param viewModel The shared ViewModel for the registration flow.
 * @param permissionsState The permissions state managed at the activity level.
 * @param modifier Modifier to be applied to the root container.
 * @param onRegistrationComplete Callback invoked when the registration process is successfully completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationHostScreen(
  registrationRepository: RegistrationRepository,
  viewModel: RegistrationViewModel,
  permissionsState: MultiplePermissionsState,
  modifier: Modifier = Modifier,
  onRegistrationComplete: () -> Unit = {}
) {
  RegistrationNavHost(
    registrationRepository = registrationRepository,
    registrationViewModel = viewModel,
    permissionsState = permissionsState,
    modifier = modifier.fillMaxSize(),
    onRegistrationComplete = onRegistrationComplete
  )
}
