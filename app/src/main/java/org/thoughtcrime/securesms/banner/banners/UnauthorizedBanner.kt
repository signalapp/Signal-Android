/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * A banner displayed when the client is unauthorized (deregistered).
 */
class UnauthorizedBanner(val context: Context) : Banner<Unit>() {

  override val enabled: Boolean
    get() = TextSecurePreferences.isUnauthorizedReceived(context) || !SignalStore.account.isRegistered

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  override val stateUpdates: Flow<Unit>
    get() = Recipient.self()
      .live()
      .observable()
      .asFlow()
      .map { enabled }
      .distinctUntilChanged()
      .map { }

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(contentPadding, SignalStore.account.isLinkedDevice)
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, isLinkedDevice: Boolean) {
  val context = LocalContext.current

  DefaultBanner(
    title = null,
    body = stringResource(
      id = if (isLinkedDevice) {
        R.string.UnauthorizedReminder_this_device_is_no_longer_linked_relink_to_continue_messaging
      } else {
        R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_Signal_on_a_different_device
      }
    ),
    importance = Importance.ERROR,
    actions = listOf(
      Action(if (isLinkedDevice) R.string.UnauthorizedReminder_relink_action else R.string.UnauthorizedReminder_reregister_action) {
        if (SignalStore.misc.isOldDeviceTransferLocked) {
          SignalStore.misc.isOldDeviceTransferLocked = false
          DeviceTransferBlockingInterceptor.getInstance().unblockNetwork()
        }

        val registrationIntent = if (isLinkedDevice) {
          RegistrationActivity.newIntentForReLinkDevice(context)
        } else {
          RegistrationActivity.newIntentForReRegistration(context)
        }
        context.startActivity(registrationIntent)
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(PaddingValues(0.dp), isLinkedDevice = false)
  }
}
