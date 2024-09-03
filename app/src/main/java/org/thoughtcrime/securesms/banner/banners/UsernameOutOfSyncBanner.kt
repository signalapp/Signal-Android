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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.AccountValues.UsernameSyncState
import org.thoughtcrime.securesms.keyvalue.SignalStore

class UsernameOutOfSyncBanner(private val context: Context, private val usernameSyncState: UsernameSyncState, private val onActionClick: (Boolean) -> Unit) : Banner() {

  override val enabled = when (usernameSyncState) {
    AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED -> true
    AccountValues.UsernameSyncState.LINK_CORRUPTED -> true
    AccountValues.UsernameSyncState.IN_SYNC -> false
  }

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    DefaultBanner(
      title = null,
      body = if (usernameSyncState == UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
        stringResource(id = R.string.UsernameOutOfSyncReminder__username_and_link_corrupt)
      } else {
        stringResource(id = R.string.UsernameOutOfSyncReminder__link_corrupt)
      },
      importance = Importance.ERROR,
      actions = listOf(
        Action(R.string.UsernameOutOfSyncReminder__fix_now) {
          onActionClick(usernameSyncState == UsernameSyncState.USERNAME_AND_LINK_CORRUPTED)
        }
      ),
      paddingValues = contentPadding
    )
  }

  companion object {

    /**
     * @param onActionClick input is true if both the username and the link are corrupted, false if only the link is corrupted
     */
    @JvmStatic
    fun createFlow(context: Context, onActionClick: (Boolean) -> Unit): Flow<UsernameOutOfSyncBanner> = createAndEmit {
      UsernameOutOfSyncBanner(context, SignalStore.account.usernameSyncState, onActionClick)
    }
  }
}
