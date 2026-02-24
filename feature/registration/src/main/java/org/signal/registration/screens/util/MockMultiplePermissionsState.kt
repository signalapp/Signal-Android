/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.util

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState

/**
 * Helpful mock for [MultiplePermissionsState] to make previews easier.
 */
class MockMultiplePermissionsState(
  override val allPermissionsGranted: Boolean = false,
  override val permissions: List<PermissionState> = emptyList(),
  override val revokedPermissions: List<PermissionState> = emptyList(),
  override val shouldShowRationale: Boolean = false
) : MultiplePermissionsState {
  override fun launchMultiplePermissionRequest() = Unit
}
