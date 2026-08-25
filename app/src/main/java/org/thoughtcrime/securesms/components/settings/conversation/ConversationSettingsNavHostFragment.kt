/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentTransaction
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfo
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfoProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class ConversationSettingsNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {

  companion object {
    /**
     * The fade/scale transition conversation settings animates in and out with, matching what the compose entry point
     * gets from `TransitionSpecs.FadeScale`.
     */
    fun FragmentTransaction.setConversationSettingsAnimations(): FragmentTransaction {
      return setCustomAnimations(R.anim.fade_scale_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_scale_out)
    }

    suspend fun createArgs(recipientId: RecipientId): Bundle {
      val recipient = withContext(Dispatchers.Default) { Recipient.resolved(recipientId) }
      val kind = ConversationSettingsKind.from(recipient)

      val args = if (recipient.isGroup) {
        ConversationSettingsFragmentArgs.Builder(null, recipient.requireGroupId(), null, kind)
      } else {
        ConversationSettingsFragmentArgs.Builder(recipientId, null, null, kind)
      }.build()

      return bundleOf(DSLSettingsActivity.ARG_START_BUNDLE to args.toBundle())
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val args = requireArguments().getBundle(DSLSettingsActivity.ARG_START_BUNDLE)
    navController.setGraph(R.navigation.conversation_settings, args)
    super.onCreate(savedInstanceState)
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> {
    return navController.currentBackStackEntryFlow.map {
      if (navController.previousBackStackEntry != null) {
        FragmentBackPressedInfo.Enabled { navController.popBackStack() }
      } else {
        FragmentBackPressedInfo.Disabled
      }
    }
  }
}
