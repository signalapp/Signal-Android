/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * A banner displayed when the client is unauthorized (deregistered).
 */
class UnauthorizedBanner(val context: Context) : Banner() {

  override val enabled = TextSecurePreferences.isUnauthorizedReceived(context) || !SignalStore.account.isRegistered

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_Signal_on_a_different_device),
      importance = Importance.ERROR,
      actions = listOf(
        Action(R.string.UnauthorizedReminder_reregister_action) {
          val registrationIntent = RegistrationActivity.newIntentForReRegistration(context)
          context.startActivity(registrationIntent)
        }
      ),
      paddingValues = contentPadding
    )
  }

  /**
   * A class that can be held by a listener but still produce new [UnauthorizedBanner] in its flow.
   * Designed for being called upon by a listener that is listening to changes in [TextSecurePreferences]
   */
  class Producer(private val context: Context) {
    private val _flow = MutableSharedFlow<Boolean>(replay = 1)
    val flow: Flow<UnauthorizedBanner> = _flow.map { UnauthorizedBanner(context) }

    init {
      queryAndEmit()
    }

    fun queryAndEmit() {
      _flow.tryEmit(TextSecurePreferences.isUnauthorizedReceived(context))
    }
  }

  companion object {
    private val TAG = Log.tag(UnauthorizedBanner::class)
  }
}
