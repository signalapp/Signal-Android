/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.link

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Lets the user tie a Signal contact to a phone contact, either one they already have or a new one
 * prefilled from Signal. Signal then shows the contact's name and photo, as for any phone contact.
 */
class LinkPhoneContactActivity : PassphraseRequiredActivity() {

  companion object {
    private const val RECIPIENT_ID = "recipient_id"

    private val CONTACTS_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    @JvmStatic
    fun createIntent(context: Context, recipientId: RecipientId): Intent {
      return Intent(context, LinkPhoneContactActivity::class.java).putExtra(RECIPIENT_ID, recipientId)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val recipientId: RecipientId = intent.getParcelableExtraCompat(RECIPIENT_ID, RecipientId::class.java)!!

    setContent {
      SignalTheme {
        LinkPhoneContactScreen(
          viewModel = viewModel { LinkPhoneContactViewModel(recipientId) },
          onLinked = {
            setResult(Activity.RESULT_OK)
            finish()
          },
          onNavigateUp = { onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }

  @Composable
  private fun LinkPhoneContactScreen(
    viewModel: LinkPhoneContactViewModel,
    onLinked: () -> Unit,
    onNavigateUp: () -> Unit
  ) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickContactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
      if (uri != null) {
        viewModel.link(uri)
      }
    }

    val createContactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val uri = result.data?.data
      if (uri != null) {
        viewModel.link(uri)
      } else if (result.resultCode == Activity.RESULT_OK) {
        viewModel.linkFailed()
      }
    }

    var pendingAction: (() -> Unit)? by remember { mutableStateOf(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      if (grants.values.all { it }) {
        pendingAction?.invoke()
      } else {
        viewModel.permissionDenied()
      }
    }

    fun withContactsPermission(action: () -> Unit) {
      if (Permissions.hasAll(context, *CONTACTS_PERMISSIONS)) {
        action()
      } else {
        pendingAction = action
        permissionLauncher.launch(CONTACTS_PERMISSIONS)
      }
    }

    val couldntLink = stringResource(R.string.LinkPhoneContactActivity__couldnt_link_that_phone_contact)
    val needsAccess = stringResource(R.string.LinkPhoneContactActivity__signal_needs_access_to_your_contacts)
    LaunchedEffect(state.result) {
      when (state.result) {
        LinkPhoneContactViewModel.Result.LINKED -> onLinked()
        LinkPhoneContactViewModel.Result.FAILED -> {
          viewModel.clearResult()
          snackbarHostState.showSnackbar(couldntLink)
        }
        LinkPhoneContactViewModel.Result.NO_PERMISSION -> {
          viewModel.clearResult()
          snackbarHostState.showSnackbar(needsAccess)
        }
        null -> Unit
      }
    }

    Scaffolds.Settings(
      title = stringResource(R.string.LinkPhoneContactActivity__link_to_phone_contact),
      onNavigationClick = onNavigateUp,
      navigationIcon = SignalIcons.ArrowStart.imageVector,
      navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
      snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
      val recipient = state.recipient ?: return@Settings

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
          .padding(paddingValues)
          .fillMaxWidth()
      ) {
        AvatarImage(
          recipient = recipient,
          modifier = Modifier
            .padding(top = 24.dp)
            .size(80.dp)
            .clip(CircleShape)
        )

        Text(
          text = stringResource(R.string.LinkPhoneContactActivity__use_a_phone_contact_for_s, recipient.getDisplayName(context)),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )

        Rows.TextRow(
          icon = SignalIcons.Search.imageVector,
          text = stringResource(R.string.LinkPhoneContactActivity__choose_a_phone_contact),
          enabled = !state.isLinking,
          onClick = { withContactsPermission { pickContactLauncher.launch(null) } },
          modifier = Modifier.fillMaxWidth()
        )

        Rows.TextRow(
          icon = SignalIcons.Plus.imageVector,
          text = stringResource(R.string.LinkPhoneContactActivity__create_a_new_phone_contact),
          enabled = !state.isLinking,
          onClick = {
            withContactsPermission {
              coroutineScope.launch { createContactLauncher.launch(viewModel.createContactIntent()) }
            }
          },
          modifier = Modifier.fillMaxWidth()
        )
      }

      if (state.isLinking) {
        Dialogs.IndeterminateProgressDialog()
      }
    }
  }
}
