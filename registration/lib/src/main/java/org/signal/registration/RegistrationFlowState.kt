/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RegistrationFlowState(
  val backStack: List<RegistrationRoute> = listOf(RegistrationRoute.Welcome),
  val sessionMetadata: NetworkController.SessionMetadata? = null,
  val sessionE164: String? = null
) : Parcelable
