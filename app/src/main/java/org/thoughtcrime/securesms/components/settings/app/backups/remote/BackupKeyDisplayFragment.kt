/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.compose.ComposeFragment

/**
 * Fragment which only displays the backup key to the user.
 */
class BackupKeyDisplayFragment : ComposeFragment() {

  companion object {
    const val AEP_ROTATION_KEY = "AEP_ROTATION_KEY"
  }

  private val args: BackupKeyDisplayFragmentArgs by navArgs()

  @Composable
  override fun FragmentContent() {
    BackupKeyDisplayEntry(
      startWithKeyRotation = args.startWithKeyRotation,
      onKeyRotated = {
        setFragmentResult(AEP_ROTATION_KEY, bundleOf(AEP_ROTATION_KEY to true))
        findNavController().popBackStack()
      },
      onClose = { findNavController().popBackStack() }
    )
  }
}
