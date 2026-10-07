/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.link

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Lists the Signal contacts that lost their link to a phone contact, such as after moving to a new
 * phone, so the user can link each one again or dismiss it.
 */
class UnlinkedSignalContactsActivity : PassphraseRequiredActivity() {

  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, UnlinkedSignalContactsActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      SignalTheme {
        UnlinkedSignalContactsScreen(
          viewModel = viewModel { UnlinkedSignalContactsViewModel() },
          onNavigateUp = { onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }
}

@Composable
private fun UnlinkedSignalContactsScreen(
  viewModel: UnlinkedSignalContactsViewModel,
  onNavigateUp: () -> Unit
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val linkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    viewModel.reload()
  }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
    if (grants.values.all { it }) {
      viewModel.onContactsPermissionGranted()
    }
  }

  Scaffolds.Settings(
    title = stringResource(R.string.UnlinkedSignalContactsActivity__unlinked_signal_contacts),
    onNavigationClick = onNavigateUp,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
    actions = {
      if (state.recipients.isNotEmpty()) {
        OverflowMenu(onDismissAll = viewModel::dismissAll)
      }
    }
  ) { paddingValues ->
    LazyColumn(modifier = Modifier.padding(paddingValues)) {
      item {
        Text(
          text = stringResource(R.string.UnlinkedSignalContactsActivity__these_contacts_were_linked),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
      }

      if (!state.hasContactsPermission) {
        item {
          Text(
            text = stringResource(R.string.UnlinkedSignalContactsActivity__allow_access_to_your_contacts),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
          )
          Buttons.Small(
            onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
          ) {
            Text(text = stringResource(R.string.UnlinkedSignalContactsActivity__allow_access))
          }
        }
      }

      if (state.isLoaded && state.recipients.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.UnlinkedSignalContactsActivity__all_your_signal_contacts_are_linked),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
          )
        }
      }

      items(state.recipients, key = { it.id.toLong() }) { recipient ->
        UnlinkedContactRow(
          recipient = recipient,
          onClick = { linkLauncher.launch(LinkPhoneContactActivity.createIntent(context, recipient.id)) },
          onDismiss = { viewModel.dismiss(recipient.id) }
        )
      }
    }
  }
}

@Composable
private fun OverflowMenu(onDismissAll: () -> Unit) {
  val menuController = remember { DropdownMenus.MenuController() }

  IconButton(onClick = { menuController.show() }) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_more_vertical),
      contentDescription = stringResource(R.string.UnlinkedSignalContactsActivity__more_options)
    )
  }

  DropdownMenus.Menu(
    controller = menuController,
    offsetX = 24.dp,
    offsetY = 0.dp
  ) {
    DropdownMenus.Item(
      text = { Text(text = stringResource(R.string.UnlinkedSignalContactsActivity__dismiss_all)) },
      onClick = {
        onDismissAll()
        menuController.hide()
      }
    )
  }
}

@Composable
private fun UnlinkedContactRow(
  recipient: Recipient,
  onClick: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
  ) {
    AvatarImage(
      recipient = recipient,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
    )

    Text(
      text = recipient.getDisplayName(context),
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp)
    )

    TextButton(onClick = onDismiss) {
      Text(text = stringResource(R.string.UnlinkedSignalContactsActivity__dismiss))
    }
  }
}
