/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

sealed interface VerifyDisplayScreenEvent {
  data class VerifyButtonClick(val isVerified: Boolean) : VerifyDisplayScreenEvent
  data object VerifyAutomaticallyClick : VerifyDisplayScreenEvent
  data object ShareClick : VerifyDisplayScreenEvent
  data object QrClick : VerifyDisplayScreenEvent
  data object YouMustFirstExchangeMessagesDialogDismiss : VerifyDisplayScreenEvent
  data object EducationDismiss : VerifyDisplayScreenEvent
  data object EducationLearnMoreClick : VerifyDisplayScreenEvent
}
