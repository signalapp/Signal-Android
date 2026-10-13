/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

/**
 * Everything [ShakeToReportDialog] needs to render.
 */
data class ShakeToReportState(
  val step: Step = Step.Prompt
) {

  /** Where the user is in the submit flow. Each step is rendered by the same dialog. */
  sealed interface Step {
    /** Asking whether the user wants to submit a log. */
    data object Prompt : Step

    /** The log is being built and uploaded. */
    data object Submitting : Step

    /** The log was uploaded to [url]. */
    data class Submitted(val url: String) : Step

    /** Building or uploading the log failed. */
    data object Failed : Step
  }
}
